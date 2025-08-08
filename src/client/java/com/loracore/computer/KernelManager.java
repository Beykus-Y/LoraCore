// Полный исправленный файл: src/client/java/com/loracore/computer/KernelManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
// ИСПРАВЛЕНИЕ 1: Добавляем импорт для ClientApi
import com.loracore.api.ClientApi;
import com.loracore.api.GpuApi;
import com.loracore.computer.kernel.*;
import com.loracore.gui.TabletScreen;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

public class KernelManager {

    private final IKernelApi api;
    private final JarClassLoader classLoader;
    private final TabletScreen parentScreen;
    private IKernel kernelInstance;
    private boolean isRunning = false;
    private String crashError = null;

    public KernelManager(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.classLoader = new JarClassLoader(getClass().getClassLoader());
        this.api = new KernelApiImpl(vfs, tabletUuid, parentScreen);
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public String getCrashMessage() {
        return this.crashError;
    }

    public void boot(String jarPath) {
        api.getVfs().readBytes(jarPath).whenComplete((jarBytesOpt, error) -> {
            // ИСПРАВЛЕНИЕ 2: Используем ClientApi для выполнения кода в основном потоке клиента
            ClientApi.executeOnRenderThread(() -> {
                if (error != null) {
                    setCrashState("VFS Error: Failed to read " + jarPath + ": " + error.getMessage());
                    return;
                }
                if (jarBytesOpt.isEmpty()) {
                    setCrashState("Boot Error: Kernel file not found at " + jarPath);
                    return;
                }
                try {
                    Map<String, byte[]> classData = unpackJar(jarBytesOpt.get());
                    byte[] manifestBytes = classData.get("META-INF/MANIFEST.MF");
                    if (manifestBytes == null) throw new IOException("JAR is missing META-INF/MANIFEST.MF");

                    Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
                    String mainClassName = manifest.getMainAttributes().getValue("Kernel-Main-Class");
                    if (mainClassName == null || mainClassName.trim().isEmpty()) {
                        throw new IOException("Manifest is missing 'Kernel-Main-Class' attribute.");
                    }

                    for (Map.Entry<String, byte[]> entry : classData.entrySet()) {
                        if (entry.getKey().endsWith(".class")) {
                            String className = entry.getKey().replace("/", ".").replace(".class", "");
                            classLoader.defineClassFromData(className, entry.getValue());
                        }
                    }

                    Class<?> kernelClass = classLoader.loadClass(mainClassName);
                    if (!IKernel.class.isAssignableFrom(kernelClass)) {
                        throw new ClassCastException("Main class " + mainClassName + " does not implement IKernel.");
                    }

                    this.kernelInstance = (IKernel) kernelClass.getConstructor().newInstance();
                    this.kernelInstance.onBoot(this.api);
                    this.isRunning = true;

                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Kernel Panic on boot", e);
                    setCrashState("Kernel Panic: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            });
        });
    }

    public void render(Object drawContext, int mouseX, int mouseY, float delta) {
        if (isRunning && kernelInstance != null) {
            try {
                // Вызываем onRender нашего ядра, передавая ему все необходимые параметры
                kernelInstance.onRender(drawContext, mouseX, mouseY, delta);
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel render crashed", e);
                setCrashState("Render thread crashed: " + e.getMessage());
            }
        }
    }

    public void tick() {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onTick();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel tick crashed", e);
                setCrashState("Tick thread crashed: " + e.getMessage());
            }
        }
    }

    public void onEvent(KernelEvent event) {
        if (isRunning && kernelInstance != null) {
            kernelInstance.onEvent(event);
        }
    }

    public void shutdown() {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onShutdown();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Kernel shutdown error", e);
            }
        }
        isRunning = false;
        kernelInstance = null;
    }

    private void setCrashState(String message) {
        LoraCoreMod.LOGGER.error("[KernelManager] " + message);
        this.crashError = message;
        this.isRunning = false;
        shutdown();
    }

    private Map<String, byte[]> unpackJar(byte[] jarData) throws IOException {
        Map<String, byte[]> result = new ConcurrentHashMap<>();
        try (JarArchiveInputStream jarStream = new JarArchiveInputStream(new ByteArrayInputStream(jarData))) {
            JarArchiveEntry entry;
            while ((entry = jarStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), jarStream.readAllBytes());
                }
            }
        }
        return result;
    }

    // --- Внутренние классы-реализации API ---
    // (Без изменений, остаются как в предыдущем ответе)

    private static class KernelApiImpl implements IKernelApi {
        private final TabletScreen parentScreen;
        private final IKernelVfs kernelVfs;
        private final IKernelGraphics graphics;

        public KernelApiImpl(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen) {
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
            this.graphics = new KernelGraphicsImpl(tabletUuid);
        }

        @Override public IKernelGraphics getGraphics() { return this.graphics; }
        @Override public IKernelVfs getVfs() { return this.kernelVfs; }
        @Override public int[] getTerminalSize() { return new int[]{parentScreen.getTabletPixelWidth(), parentScreen.getTabletPixelHeight()}; }
        @Override public void reboot() { parentScreen.reboot(); }
        @Override public void shutdown() { parentScreen.close(); }
        @Override public int runLuaScript(String path) { return -1; }
        @Override public void sendToLua(int threadId, Object... message) {}
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
                if (luaValue.isnil() || !luaValue.isstring()) return Optional.empty();
                try {
                    return Optional.of(Base64.getDecoder().decode(luaValue.tojstring()));
                } catch (IllegalArgumentException e) {
                    LoraCoreMod.LOGGER.error("Failed to decode Base64 from VFS: {}", e.getMessage());
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
                if (luaValue.isnil() || !luaValue.isstring()) return List.of();
                try {
                    return GSON.fromJson(luaValue.tojstring(), new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                } catch (Exception e) {
                    return List.of();
                }
            });
        }
    }

    private static class KernelGraphicsImpl implements IKernelGraphics {
        private final UUID tabletUuid;
        public KernelGraphicsImpl(UUID tabletUuid) { this.tabletUuid = tabletUuid; }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            int width = x2 - x1;
            int height = y2 - y1;
            if (width <= 0 || height <= 0) return;
            GpuApi.sendCommand(this.tabletUuid, new com.loracore.network.graphics.GpuCommand.Fill(x1, y1, width, height, color));
        }

        @Override
        public void drawString(String text, int x, int y, int color) {
            GpuApi.sendCommand(this.tabletUuid, new com.loracore.network.graphics.GpuCommand.DrawText(x, y, text, color));
        }

        @Override public int getStringWidth(String text) { return text.length() * 6; }
        @Override public void beginFrame() {}
        @Override public void endFrame() {}
        @Override public void flush() {}
        @Override public void drawCenteredString(String text, int centerX, int y, int color) { drawString(text, centerX - (getStringWidth(text) / 2), y, color); }
        @Override public void pushMatrix() {}
        @Override public void popMatrix() {}
        @Override public void translate(double x, double y, double z) {}
        @Override public void enableScissor(int x, int y, int w, int h) {}
        @Override public void disableScissor() {}
    }
}