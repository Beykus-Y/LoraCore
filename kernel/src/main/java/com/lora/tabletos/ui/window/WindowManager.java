// Файл: kernel/src/main/java/com/lora/tabletos/ui/window/WindowManager.java
package com.lora.tabletos.ui.window;

import com.lora.tabletos.core.ApplicationApiImpl;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.core.JarClassLoader;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lora.tabletos.ui.system.NotificationManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

/**
 * Управляет жизненным циклом полноэкранных приложений и переключением между ними.
 * Работает по принципу мобильной ОС, а не традиционного оконного менеджера.
 */
public class WindowManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowManager.class);
    private final IKernelApi api;
    private final JarClassLoader classLoader;
    private final IApplicationApi appApi;
    private final NotificationManager notificationManager;

    // Внутренний record для хранения экземпляра приложения и его пути
    private record AppInstance(IApplication app, String path) {}

    // Публичный record для передачи информации о приложении наружу (например, в NavigationBar)
    public record AppInfo(String path) {}

    // Хранилище запущенных приложений: UUID экземпляра -> Экземпляр
    private final Map<UUID, AppInstance> runningApps = new ConcurrentHashMap<>();
    // Отслеживает порядок запуска/фокуса, последний элемент - самый активный
    private final List<UUID> appFocusOrder = Collections.synchronizedList(new ArrayList<>());

    // ID активного (видимого) приложения. null, если мы на рабочем столе.
    private UUID activeAppId = null;

    public WindowManager(IKernelApi api, NotificationManager notificationManager) {
        this.api = api;
        this.notificationManager = notificationManager; // Сохраняем
        this.classLoader = new JarClassLoader(getClass().getClassLoader());

        // Передаем менеджер в API
        this.appApi = new ApplicationApiImpl(api, notificationManager);
    }

    /**
     * Отрисовывает только активное приложение.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        IApplication activeApp = getActiveApp();
        if (activeApp != null) {
            try {
                activeApp.onRender(g, mouseX, mouseY, delta);
            } catch (Exception e) {
                LOGGER.error("Ошибка при рендеринге приложения с ID: {}", activeAppId, e);
                closeApp(activeAppId); // Закрываем аварийное приложение
            }
        }
    }

    /**
     * Передает событие только активному приложению.
     */
    public boolean handleEvent(KernelEvent event) {
        IApplication activeApp = getActiveApp();
        if (activeApp != null) {
            try {
                return activeApp.onEvent(event); // Событие было передано активному приложению
            } catch (Exception e) {
                LOGGER.error("Ошибка при обработке события в приложении с ID: {}", activeAppId, e);
                closeApp(activeAppId); // Закрываем аварийное приложение
                return true; // Считаем событие обработанным, чтобы избежать дальнейших ошибок
            }
        }
        return false; // Нет активного приложения для обработки события
    }

    /**
     * Запускает приложение. Если оно уже запущено, просто переключается на него.
     * @param path Путь к файлу приложения (.jar или .lua)
     */
    public void launchApp(String path) {
        LOGGER.info("WindowManager: Попытка запуска приложения {}", path);

        Optional<UUID> existingInstanceId = findAppByPath(path);
        if (existingInstanceId.isPresent()) {
            LOGGER.info("Приложение {} уже запущено. Переключаемся на экземпляр {}.", path, existingInstanceId.get());
            switchToApp(existingInstanceId.get());
            return;
        }

        if (path.endsWith(".jar")) {
            launchJarApp(path);
        } else if (path.endsWith(".lua")) {
            LOGGER.error("Lua-приложения больше не поддерживаются.");
        } else {
            LOGGER.error("Неподдерживаемый тип приложения: {}", path);
        }
    }

    /**
     * Запускает встроенное приложение по его классу.
     * @param appClass Класс приложения, реализующий IApplication
     */
    public void launchApp(Class<? extends IApplication> appClass) {
        String appName = appClass.getSimpleName();
        LOGGER.info("WindowManager: Попытка запуска встроенного приложения {}", appName);

        // Проверяем, не запущено ли уже это приложение
        Optional<UUID> existingInstanceId = runningApps.entrySet().stream()
                .filter(entry -> entry.getValue().app().getClass() == appClass)
                .map(Map.Entry::getKey)
                .findFirst();

        if (existingInstanceId.isPresent()) {
            LOGGER.info("Встроенное приложение {} уже запущено. Переключаемся на экземпляр {}.", appName, existingInstanceId.get());
            switchToApp(existingInstanceId.get());
            return;
        }

        try {
            IApplication newApp = appClass.getDeclaredConstructor().newInstance();
            UUID newAppId = UUID.randomUUID();
            // Используем специальный путь для встроенных приложений
            String builtInPath = "builtin://" + appName;

            runningApps.put(newAppId, new AppInstance(newApp, builtInPath));
            LOGGER.info("Встроенное приложение '{}' успешно загружено. ID экземпляра: {}", appName, newAppId);

            newApp.onLoad(appApi);
            switchToApp(newAppId);
        } catch (Exception e) {
            LOGGER.error("Критическая ошибка при запуске встроенного приложения: {}", appName, e);
        }
    }

    /**
     * Переключает фокус на указанное приложение.
     * @param appId UUID экземпляра приложения для активации. Если null, возвращает на рабочий стол.
     */
    public void switchToApp(UUID appId) {
        if (Objects.equals(activeAppId, appId)) {
            return; // Уже активно, ничего не делаем
        }

        // 1. Уведомляем старое приложение (если оно было), что оно уходит в фон
        IApplication oldApp = getActiveApp();
        if (oldApp != null) {
            try {
                oldApp.onPause();
            } catch (Exception e) {
                LOGGER.error("Ошибка при вызове onPause() для приложения {}: {}", activeAppId, e.getMessage());
            }
        }

        // 2. Меняем активное приложение
        this.activeAppId = appId;

        // 3. Уведомляем новое приложение (если оно есть), что оно стало активным
        IApplication newApp = getActiveApp();
        if (newApp != null) {
            try {
                newApp.onResume();
            } catch (Exception e) {
                LOGGER.error("Ошибка при вызове onResume() для приложения {}: {}", activeAppId, e.getMessage());
                // Если при активации произошел сбой, закрываем это приложение
                closeApp(activeAppId);
                return; // Прерываем выполнение, так как приложение уже закрыто
            }
        }

        // 4. Обновляем порядок фокуса для переключения "назад"
        if (appId != null) {
            appFocusOrder.remove(appId);
            appFocusOrder.add(appId);
        }
        LOGGER.info("Активное приложение изменено на: {}", appId);
    }

    /**
     * Закрывает приложение по его ID.
     * @param appId UUID экземпляра приложения для закрытия.
     */
    public void closeApp(UUID appId) {
        appFocusOrder.remove(appId);
        AppInstance instance = runningApps.remove(appId);

        if (instance != null) {
            try {
                instance.app().onClose();
                LOGGER.info("Приложение '{}' ({}) было закрыто.", instance.path(), appId);
            } catch (Exception e) {
                LOGGER.error("Ошибка при закрытии приложения '{}' ({}).", instance.path(), appId, e);
            }
        }

        if (Objects.equals(activeAppId, appId)) {
            UUID previousAppId = appFocusOrder.isEmpty() ? null : appFocusOrder.get(appFocusOrder.size() - 1);
            switchToApp(previousAppId);
        }
    }

    /**
     * Корректно завершает работу всех запущенных приложений.
     */
    public void shutdown() {
        LOGGER.info("WindowManager завершает работу... Закрытие {} приложений.", runningApps.size());
        new ArrayList<>(runningApps.keySet()).forEach(this::closeApp);
    }

    public boolean hasActiveApp() {
        return activeAppId != null;
    }

    // --- ГЕТТЕРЫ ДЛЯ NAVIGATIONBAR ---

    public IApplication getActiveApp() {
        if (activeAppId == null) return null;
        AppInstance instance = runningApps.get(activeAppId);
        return instance != null ? instance.app() : null;
    }

    public Map<UUID, AppInfo> getRunningApps() {
        Map<UUID, AppInfo> infoMap = new HashMap<>();
        runningApps.forEach((uuid, appInstance) -> infoMap.put(uuid, new AppInfo(appInstance.path())));
        return Collections.unmodifiableMap(infoMap);
    }

    public UUID getActiveAppId() {
        return this.activeAppId;
    }

    // --- Приватные методы ---

    private void launchLuaApp(String path) {
        LOGGER.warn("Запуск Lua-приложений недоступен: поддержка Lua удалена.");
    }

    private void launchJarApp(String path) {
        api.getVfs().readBytes(path).whenComplete((jarBytesOpt, error) -> {
            if (error != null || jarBytesOpt.isEmpty()) {
                LOGGER.error("Не удалось прочитать JAR-файл: {}", path, error);
                return;
            }
            try {
                Map<String, byte[]> classData = unpackJar(jarBytesOpt.get());
                Manifest manifest = new Manifest(new ByteArrayInputStream(classData.get("META-INF/MANIFEST.MF")));
                String mainClassName = manifest.getMainAttributes().getValue("App-Main-Class");

                for (Map.Entry<String, byte[]> entry : classData.entrySet()) {
                    if (entry.getKey().endsWith(".class")) {
                        String className = entry.getKey().replace("/", ".").replace(".class", "");
                        classLoader.defineClassFromData(className, entry.getValue());
                    }
                }

                Class<?> appClass = classLoader.loadClass(mainClassName);
                if (!IApplication.class.isAssignableFrom(appClass)) {
                    LOGGER.error("Главный класс {} не реализует IApplication", mainClassName);
                    return;
                }

                IApplication newApp = (IApplication) appClass.getDeclaredConstructor().newInstance();
                UUID newAppId = UUID.randomUUID();

                runningApps.put(newAppId, new AppInstance(newApp, path));
                LOGGER.info("Приложение '{}' успешно загружено. ID экземпляра: {}", path, newAppId);

                newApp.onLoad(appApi);
                switchToApp(newAppId);

            } catch (Exception e) {
                LOGGER.error("Критическая ошибка при запуске JAR-приложения: {}", path, e);
            }
        });
    }

    private Optional<UUID> findAppByPath(String path) {
        return runningApps.entrySet().stream()
                .filter(entry -> entry.getValue().path().equals(path))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private Map<String, byte[]> unpackJar(byte[] jarData) throws IOException {
        Map<String, byte[]> classData = new ConcurrentHashMap<>();
        try (var zipStream = new java.util.zip.ZipInputStream(new ByteArrayInputStream(jarData))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    classData.put(entry.getName(), zipStream.readAllBytes());
                }
            }
        }
        return classData;
    }
    public AppInfo getActiveAppInfo() {
        if (activeAppId == null) return null;
        return runningApps.entrySet().stream()
                .filter(e -> e.getKey().equals(activeAppId))
                .map(e -> new AppInfo(e.getValue().path()))
                .findFirst().orElse(null);
    }
}
