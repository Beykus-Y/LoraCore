// Полный исправленный файл: src/client/java/com/loracore/computer/KernelManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.kernel.*;
import com.loracore.gui.TabletScreen;
import com.loracore.network.InvokeDeviceMethodC2SPacket;
import com.loracore.network.RunLuaScriptC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class KernelManager {

    private final IKernelApi api;
    private final TabletScreen parentScreen;
    private IKernel kernelInstance;
    private boolean isRunning = false;
    private String crashError = null;

    // Конструктор теперь не создает ненужный ClassLoader
    public KernelManager(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
        this.parentScreen = parentScreen;
        this.api = new KernelApiImpl(vfs, tabletUuid, parentScreen, screenImage, isOwner);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public String getCrashMessage() {
        return this.crashError;
    }
    public void updateGraphics(net.minecraft.client.texture.NativeImage newScreenImage) {
        if (api instanceof KernelApiImpl apiImpl) {
            // 1. Пересоздаем графику внутри старого API
            apiImpl.recreateGraphics(newScreenImage);

            // ✅ ИСПРАВЛЕНИЕ: 2. Уведомляем ядро о том, что API обновился,
            // и передаем ему этот обновленный экземпляр.
            if (this.kernelInstance != null) {
                this.kernelInstance.onApiUpdate(this.api);
            }
        }
    }

    /**
     * Полностью переписанный метод загрузки.
     * Он сохраняет полученный JAR во временный файл и загружает его целиком через URLClassLoader.
     */
    public void boot(String jarPath) {
        api.getVfs().readBytes(jarPath).whenComplete((jarBytesOpt, error) -> {
            ClientApi.executeOnRenderThread(() -> {
                if (error != null || jarBytesOpt.isEmpty()) {
                    setCrashState("VFS Error: Failed to read kernel at " + jarPath);
                    return;
                }

                File tempJarFile = null;
                try {
                    // 1. Создаем временный файл для нашего JAR-а.
                    tempJarFile = File.createTempFile("loracore_kernel_", ".jar");

                    // 2. Записываем полученные из VFS байты во временный файл.
                    try (FileOutputStream fos = new FileOutputStream(tempJarFile)) {
                        fos.write(jarBytesOpt.get());
                    }

                    // 3. Получаем URL этого временного файла.
                    URL[] urls = { tempJarFile.toURI().toURL() };

                    // 4. Создаем НОВЫЙ экземпляр JarClassLoader, который теперь ЗНАЕТ о нашем JAR-файле.
                    // Теперь он сможет сам находить все классы внутри, включая их зависимости (IWidget).
                    JarClassLoader kernelClassLoader = new JarClassLoader(urls, getClass().getClassLoader());

                    // 5. Читаем манифест, чтобы узнать главный класс.
                    Manifest manifest;
                    try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytesOpt.get()))) {
                        manifest = jis.getManifest();
                    }

                    if (manifest == null) {
                        throw new IOException("JAR file does not contain a valid MANIFEST.MF");
                    }
                    String mainClassName = manifest.getMainAttributes().getValue("Kernel-Main-Class");

                    if (mainClassName == null || mainClassName.trim().isEmpty()) {
                        throw new IOException("Manifest is missing 'Kernel-Main-Class' attribute.");
                    }

                    // 6. Загружаем главный класс ядра, используя уже "умный" загрузчик.
                    Class<?> kernelClass = kernelClassLoader.loadClass(mainClassName);

                    if (!IKernel.class.isAssignableFrom(kernelClass)) {
                        throw new ClassCastException("Main class " + mainClassName + " does not implement IKernel.");
                    }

                    // 7. Создаем экземпляр и запускаем.
                    this.kernelInstance = (IKernel) kernelClass.getConstructor().newInstance();
                    this.kernelInstance.onBoot(this.api);
                    this.isRunning = true;

                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Kernel Panic on boot", e);
                    setCrashState("Kernel Panic: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    // 8. Обязательно удаляем временный файл после использования, чтобы не засорять систему.
                    if (tempJarFile != null) {
                        tempJarFile.delete();
                    }
                }
            });
        });
    }

    public void render(int mouseX, int mouseY, float delta) {
        if (!isRunning || kernelInstance == null) return;
        kernelInstance.onRender(mouseX, mouseY, delta);
    }

    public void tick() {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onTick();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel Panic on tick", e);
                setCrashState("Kernel Panic: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    public void onEvent(KernelEvent event) {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onEvent(event);
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel Panic on event", e);
                setCrashState("Kernel Panic: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onShutdown();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel Panic on shutdown", e);
            }
        }
        this.isRunning = false;
        this.kernelInstance = null;
        this.crashError = null;
    }

    public void setLuaExecutor(java.util.function.Consumer<String> executor) {
        if (api instanceof KernelApiImpl) {
            ((KernelApiImpl) api).setLuaExecutor(executor);
        }
    }

    private void setCrashState(String message) {
        this.crashError = message;
        this.isRunning = false;
        LoraCoreMod.LOGGER.error("Kernel crashed: {}", message);
    }
    /**
     * НОВЫЙ МЕТОД, который нужно добавить.
     * Он будет принимать вызов от TabletScreen и передавать его дальше
     * внутреннему обработчику API.
     */
    public void onDeviceResult(int requestId, boolean success, Object[] result) {
        // Проверяем, что наш api является экземпляром KernelApiImpl
        if (this.api instanceof KernelApiImpl apiImpl) {
            apiImpl.onDeviceResult(requestId, success, result);
        }
    }

    // Метод unpackJar больше не нужен и был удален.

    private static class KernelApiImpl implements IKernelApi {
        private final TabletScreen parentScreen;
        private final IKernelVfs kernelVfs;
        private IKernelGraphics graphics;
        private final UUID tabletUuid;
        private final Map<Integer, CompletableFuture<Object[]>> pendingDeviceRequests = new ConcurrentHashMap<>();
        private final AtomicInteger nextRequestId = new AtomicInteger(0);

        public KernelApiImpl(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
            this.tabletUuid = tabletUuid;

            if (isOwner) {
                this.graphics = new com.loracore.computer.jkernel.ClientSideGraphics(screenImage, net.minecraft.client.MinecraftClient.getInstance().getResourceManager());
            } else {
                this.graphics = new com.loracore.computer.jkernel.ServerSideGraphics(tabletUuid);
            }
        }

        public void recreateGraphics(net.minecraft.client.texture.NativeImage newScreenImage) {
            // Пересоздаем объект ClientSideGraphics с новой, "живой" ссылкой на NativeImage
            this.graphics = new com.loracore.computer.jkernel.ClientSideGraphics(
                    newScreenImage,
                    net.minecraft.client.MinecraftClient.getInstance().getResourceManager()
            );
        }

        @Override
        public IKernelGraphics getGraphics() {
            return this.graphics;
        }

        @Override
        public IKernelVfs getVfs() {
            return this.kernelVfs;
        }

        @Override
        public int[] getTerminalSize() {
            return new int[]{480, 270};
        }

        @Override
        public void reboot() {
            parentScreen.reboot();
        }

        @Override
        public void shutdown() {
            parentScreen.close();
        }

        @Override
        public CompletableFuture<Boolean> runLuaScript(String path) {
            ClientPlayNetworking.send(new RunLuaScriptC2SPacket(this.tabletUuid, path));
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public void setLuaExecutor(Consumer<String> executor) {
            // Реализация может быть добавлена позже, если потребуется
        }

        @Override
        public void sendToLua(int threadId, Object... message) {
            // Реализация может быть добавлена позже
        }
        @Override
        public CompletableFuture<Object[]> invokeDevice(String deviceType, String methodName, Object... args) {
            int requestId = nextRequestId.getAndIncrement();
            CompletableFuture<Object[]> future = new CompletableFuture<>();
            pendingDeviceRequests.put(requestId, future);

            String argsJson = InvokeDeviceMethodC2SPacket.argsToJson(args);
            ClientPlayNetworking.send(new InvokeDeviceMethodC2SPacket(this.tabletUuid, requestId, deviceType, methodName, argsJson));

            return future;
        }
        public void onDeviceResult(int requestId, boolean success, Object[] result) {
            CompletableFuture<Object[]> future = pendingDeviceRequests.remove(requestId);
            if (future != null) {
                if (success) {
                    future.complete(result);
                } else {
                    future.completeExceptionally(new RuntimeException((String) result[0]));
                }
            }
        }
    }

    private static class KernelVfsImpl implements IKernelVfs {
        private final ClientVFS vfs;
        private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

        public KernelVfsImpl(ClientVFS vfs) { this.vfs = vfs; }

        @Override public CompletableFuture<Boolean> exists(String path) { return vfs.existsAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean()); }
        @Override public CompletableFuture<Boolean> isDirectory(String path) { return vfs.isDirectoryAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean()); }

        @Override
        public CompletableFuture<Optional<byte[]>> readBytes(String path) {
            return vfs.readBytesAsync(path).thenApply(luaValue -> {
                if (luaValue.isnil()) return Optional.empty();
                try {
                    String base64Data = luaValue.tojstring();
                    return Optional.of(Base64.getDecoder().decode(base64Data));
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Failed to decode base64 data from path: {}", path, e);
                    return Optional.empty();
                }
            });
        }


        @Override public CompletableFuture<Boolean> writeBytes(String path, byte[] data) { return vfs.writeAsync(path, Base64.getEncoder().encodeToString(data)).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean()); }
        @Override public CompletableFuture<Boolean> makeDir(String path) { return vfs.makeDirAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean()); }
        @Override public CompletableFuture<Boolean> delete(String path) { return vfs.deleteAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean()); }

        @Override
        public CompletableFuture<List<String>> list(String path) {
            return vfs.listAsync(path).thenApply(luaValue -> {
                if (luaValue.isnil()) return List.of();
                try {
                    String jsonData = luaValue.tojstring();
                    String[] items = GSON.fromJson(jsonData, String[].class);
                    return List.of(items);
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Failed to parse list data from path: {}", path, e);
                    return List.of();
                }
            });
        }
    }
}