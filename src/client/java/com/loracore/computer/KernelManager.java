// Полный исправленный файл: src/client/java/com/loracore/computer/KernelManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
// ИСПРАВЛЕНИЕ 1: Добавляем импорт для ClientApi
import com.loracore.api.ClientApi;
import com.loracore.api.GpuApi;
import com.loracore.computer.kernel.*;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;
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
import java.util.function.Consumer;
import java.lang.reflect.Method;

public class KernelManager {

    private final IKernelApi api;
    private final JarClassLoader classLoader;
    private final TabletScreen parentScreen;
    private IKernel kernelInstance;
    private boolean isRunning = false;
    private String crashError = null;

    public KernelManager(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage) {
        this.parentScreen = parentScreen;
        this.classLoader = new JarClassLoader(getClass().getClassLoader());
        this.api = new KernelApiImpl(vfs, tabletUuid, parentScreen, screenImage);
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
                    LoraCoreMod.LOGGER.info("========== KERNEL CLASS DIAGNOSTICS START ==========");
                    LoraCoreMod.LOGGER.info("Loaded Class Name: {}", kernelClass.getName());
                    LoraCoreMod.LOGGER.info("Is assignable from IKernel? {}", IKernel.class.isAssignableFrom(kernelClass));
                    LoraCoreMod.LOGGER.info("Methods found in loaded class:");
                    for (Method method : kernelClass.getDeclaredMethods()) {
                        LoraCoreMod.LOGGER.info(" - {}", method.toString());
                    }
                    LoraCoreMod.LOGGER.info("========== KERNEL CLASS DIAGNOSTICS END ==========");
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

    // === ГЛАВНЫЙ МЕТОД РЕНДЕРИНГА ===
    public void render(int mouseX, int mouseY, float delta) {
        if (!isRunning || kernelInstance == null) return;
        // Ядро само рисует в буфер через IKernelGraphics
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

    private Map<String, byte[]> unpackJar(byte[] jarData) throws IOException {
        Map<String, byte[]> classData = new ConcurrentHashMap<>();
        try (JarArchiveInputStream jarStream = new JarArchiveInputStream(new ByteArrayInputStream(jarData))) {
            JarArchiveEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] data = jarStream.readAllBytes();
                    classData.put(entry.getName(), data);
                }
            }
        }
        return classData;
    }

    private static class KernelApiImpl implements IKernelApi {
        private final TabletScreen parentScreen;
        private final IKernelVfs kernelVfs;
        private final IKernelGraphics graphics;
        private Consumer<String> luaExecutor;

        public KernelApiImpl(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage) {
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
            // Для Java-приложений всегда используем серверную отрисовку
            this.graphics = new KernelGraphicsImpl(tabletUuid);
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
            if (luaExecutor == null) {
                LoraCoreMod.LOGGER.error("Lua executor not set in KernelApiImpl.");
                return CompletableFuture.completedFuture(false);
            }

            return kernelVfs.readBytes(path).thenApply(bytesOptional -> {
                if (bytesOptional.isEmpty()) {
                    LoraCoreMod.LOGGER.error("Failed to read Lua script from VFS path: {}", path);
                    return false;
                }
                try {
                    String scriptContent = new String(bytesOptional.get(), java.nio.charset.StandardCharsets.UTF_8);
                    // Просто вызываем центральный executor, который сам создаст и установит activeShell
                    luaExecutor.accept(scriptContent);
                    return true;
                } catch (Exception e) {
                     LoraCoreMod.LOGGER.error("Failed to decode Lua script from path {}: {}", path, e.getMessage());
                    return false;
                }
            });
        }

        @Override
        public void setLuaExecutor(Consumer<String> executor) {
            this.luaExecutor = executor;
        }

        @Override 
        public void sendToLua(int threadId, Object... message) {
            // Пока не реализовано
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

    private static class KernelGraphicsImpl implements IKernelGraphics {
        private final UUID tabletUuid;
        public KernelGraphicsImpl(UUID tabletUuid) { this.tabletUuid = tabletUuid; }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            // Отправляем команду на сервер через GPU API
            int width = x2 - x1;
            int height = y2 - y1;
            if (width <= 0 || height <= 0) return;
            GpuApi.sendCommand(this.tabletUuid, new com.loracore.network.graphics.GpuCommand.Fill(x1, y1, width, height, color));
        }

        @Override
        public void drawString(String text, int x, int y, int color) {
            // Отправляем команду на сервер через GPU API
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

        // ===== ЗАГЛУШКИ ДЛЯ НИЗКОУРОВНЕВЫХ МЕТОДОВ =====
        @Override public void setPixel(int x, int y, int color) { /* Не реализовано для серверного рендеринга */ }
        @Override public int getPixel(int x, int y) { return 0; }
        @Override public int getWidth() { return 480; /* Возвращаем стандартный размер */ }
        @Override public int getHeight() { return 270; }
    }

    // Клиентская реализация графики, напрямую рисующая в NativeImage
    private static class ClientSideGraphics implements IKernelGraphics {
        private final net.minecraft.client.texture.NativeImage screenImage;
        private final net.minecraft.client.font.TextRenderer textRenderer;

        public ClientSideGraphics(net.minecraft.client.texture.NativeImage screenImage) {
            this.screenImage = screenImage;
            this.textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int color) {
            // Перевод ARGB (MC) -> ABGR (NativeImage)
            int abgr = (color & 0xFF000000)
                    | ((color & 0x00FF0000) >> 16)
                    | (color & 0x0000FF00)
                    | ((color & 0x000000FF) << 16);
            int width = Math.max(0, x2 - x1);
            int height = Math.max(0, y2 - y1);
            int maxX = Math.min(screenImage.getWidth(), x1 + width);
            int maxY = Math.min(screenImage.getHeight(), y1 + height);
            int startX = Math.max(0, x1);
            int startY = Math.max(0, y1);
            for (int y = startY; y < maxY; y++) {
                for (int x = startX; x < maxX; x++) {
                    screenImage.setColor(x, y, abgr);
                }
            }
        }

        @Override
        public void drawString(String text, int x, int y, int color) {
            // Простая отрисовка текста как прямоугольников
            if (text == null || text.isEmpty()) return;
            
            int charWidth = 6;
            int charHeight = 8;
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int charX = x + i * charWidth;
                
                // Рисуем простой прямоугольник для каждого символа
                if (charX >= 0 && charX < screenImage.getWidth() && y >= 0 && y + charHeight < screenImage.getHeight()) {
                    int abgr = (color & 0xFF000000) | ((color & 0x00FF0000) >> 16) | (color & 0x0000FF00) | ((color & 0x000000FF) << 16);
                    for (int dy = 0; dy < charHeight; dy++) {
                        for (int dx = 0; dx < charWidth; dx++) {
                            screenImage.setColor(charX + dx, y + dy, abgr);
                        }
                    }
                }
            }
        }

        @Override public int getStringWidth(String text) { return text == null ? 0 : text.length() * 6; }
        @Override public void drawCenteredString(String text, int centerX, int y, int color) { drawString(text, centerX - (getStringWidth(text) / 2), y, color); }
        @Override public void beginFrame() {}
        @Override public void endFrame() {}
        @Override public void flush() {}
        @Override public void pushMatrix() {}
        @Override public void popMatrix() {}
        @Override public void translate(double x, double y, double z) {}
        @Override public void enableScissor(int x, int y, int w, int h) {}
        @Override public void disableScissor() {}

        // ===== РЕАЛИЗАЦИЯ НИЗКОУРОВНЕВЫХ МЕТОДОВ =====

        @Override
        public void setPixel(int x, int y, int color) {
            if (x >= 0 && x < screenImage.getWidth() && y >= 0 && y < screenImage.getHeight()) {
                // Конвертируем стандартный ARGB в ABGR, который использует NativeImage
                int abgr = (color & 0xFF000000) | ((color & 0x00FF0000) >> 16) | (color & 0x0000FF00) | ((color & 0x000000FF) << 16);
                screenImage.setColor(x, y, abgr);
            }
        }

        @Override
        public int getPixel(int x, int y) {
            if (x >= 0 && x < screenImage.getWidth() && y >= 0 && y < screenImage.getHeight()) {
                int abgr = screenImage.getColor(x, y);
                // Конвертируем обратно из ABGR в ARGB для пользователя API
                return (abgr & 0xFF000000) | ((abgr & 0x00FF0000) >> 16) | (abgr & 0x0000FF00) | ((abgr & 0x000000FF) << 16);
            }
            return 0; // Возвращаем черный цвет, если вышли за пределы
        }

        @Override
        public int getWidth() {
            return screenImage.getWidth();
        }

        @Override
        public int getHeight() {
            return screenImage.getHeight();
        }
    }
}