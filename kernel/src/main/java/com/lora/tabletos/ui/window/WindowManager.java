package com.lora.tabletos.ui.window;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.core.ApplicationApiImpl;
import com.lora.tabletos.core.JarClassLoader;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.jar.Manifest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет окнами приложений и активными приложениями.
 */
public class WindowManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowManager.class);
    private final IKernelApi api;
    private final JarClassLoader classLoader;
    private IApplication currentApp;
    private IApplicationApi appApi;
    
    public WindowManager(IKernelApi api) {
        this.api = api;
        this.classLoader = new JarClassLoader(getClass().getClassLoader());
        this.appApi = new ApplicationApiImpl(api);
    }
    
    /**
     * Рисуем активное приложение или ничего, если нет активного приложения.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        if (currentApp != null) {
            try {
                currentApp.onRender(g, mouseX, mouseY, delta);
            } catch (Exception e) {
                LOGGER.error("Error rendering application", e);
                closeCurrentApp();
            }
        }
    }
    
    /**
     * Передаем событие активному приложению.
     */
    public boolean handleEvent(KernelEvent event) {
        if (currentApp != null) {
            try {
                currentApp.onEvent(event);
                return true; // Событие обработано приложением
            } catch (Exception e) {
                LOGGER.error("Error handling event in application", e);
                closeCurrentApp();
                return false;
            }
        }
        return false;
    }
    
    /**
     * Закрываем все окна при выключении.
     */
    public void shutdown() {
        if (currentApp != null) {
            try {
                currentApp.onClose();
            } catch (Exception e) {
                LOGGER.error("Error closing application", e);
            }
            currentApp = null;
        }
        LOGGER.info("WindowManager shutting down...");
    }
    
    /**
     * Запускает новое приложение.
     * @param path Путь к приложению (.lua или .jar)
     */
    public void launchApp(String path) {
        LOGGER.info("WindowManager: Attempting to launch {}", path);
        
        if (path.endsWith(".lua")) {
            launchLuaApp(path);
        } else if (path.endsWith(".jar")) {
            launchJarApp(path);
        } else {
            LOGGER.error("Unsupported application type: {}", path);
        }
    }
    
    /**
     * Запускает Lua-приложение.
     */
    private void launchLuaApp(String path) {
        // Закрываем текущее приложение, если есть
        closeCurrentApp();
        
        // Запускаем Lua-скрипт
        api.runLuaScript(path).whenComplete((success, throwable) -> {
            if (throwable != null || !success) {
                LOGGER.error("Failed to launch Lua app: {}", path, throwable);
            } else {
                LOGGER.info("Lua app launched successfully: {}", path);
            }
        });
    }
    
    /**
     * Запускает JAR-приложение.
     */
    private void launchJarApp(String path) {
        // Закрываем текущее приложение, если есть
        closeCurrentApp();
        
        api.getVfs().readBytes(path).whenComplete((jarBytesOpt, error) -> {
            if (error != null) {
                LOGGER.error("Failed to read JAR file: {}", path, error);
                return;
            }
            if (jarBytesOpt.isEmpty()) {
                LOGGER.error("JAR file not found: {}", path);
                return;
            }
            
            try {
                Map<String, byte[]> classData = unpackJar(jarBytesOpt.get());
                byte[] manifestBytes = classData.get("META-INF/MANIFEST.MF");
                if (manifestBytes == null) {
                    LOGGER.error("JAR is missing META-INF/MANIFEST.MF");
                    return;
                }
                
                Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
                String mainClassName = manifest.getMainAttributes().getValue("App-Main-Class");
                if (mainClassName == null || mainClassName.trim().isEmpty()) {
                    LOGGER.error("Manifest is missing 'App-Main-Class' attribute");
                    return;
                }
                
                // Загружаем все классы из JAR
                for (Map.Entry<String, byte[]> entry : classData.entrySet()) {
                    if (entry.getKey().endsWith(".class")) {
                        String className = entry.getKey().replace("/", ".").replace(".class", "");
                        classLoader.defineClassFromData(className, entry.getValue());
                    }
                }
                
                // Загружаем главный класс приложения
                Class<?> appClass = classLoader.loadClass(mainClassName);
                if (!IApplication.class.isAssignableFrom(appClass)) {
                    LOGGER.error("Main class {} does not implement IApplication", mainClassName);
                    return;
                }
                
                // Создаем экземпляр приложения
                currentApp = (IApplication) appClass.getConstructor().newInstance();
                currentApp.onLoad(appApi);
                LOGGER.info("JAR app launched successfully: {}", path);
                
            } catch (Exception e) {
                LOGGER.error("Failed to launch JAR app: {}", path, e);
            }
        });
    }
    
    /**
     * Закрывает текущее приложение.
     */
    public void closeCurrentApp() {
        if (currentApp != null) {
            try {
                currentApp.onClose();
            } catch (Exception e) {
                LOGGER.error("Error closing current application", e);
            }
            currentApp = null;
        }
    }
    
    /**
     * Проверяет, есть ли активное приложение.
     */
    public boolean hasActiveApp() {
        return currentApp != null;
    }
    
    /**
     * Распаковывает JAR-файл в Map.
     */
    private Map<String, byte[]> unpackJar(byte[] jarData) throws IOException {
        Map<String, byte[]> classData = new ConcurrentHashMap<>();
        try (java.util.zip.ZipInputStream zipStream = new java.util.zip.ZipInputStream(new ByteArrayInputStream(jarData))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] data = zipStream.readAllBytes();
                    classData.put(entry.getName(), data);
                }
            }
        }
        return classData;
    }
}
