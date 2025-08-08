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

    public KernelManager(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
        this.parentScreen = parentScreen;
        this.classLoader = new JarClassLoader(getClass().getClassLoader());
        this.api = new KernelApiImpl(vfs, tabletUuid, parentScreen, screenImage, isOwner);
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

        public KernelApiImpl(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
            
            // Логика выбора драйвера
            if (isOwner) {
                // Для владельца планшета используем клиентский рендеринг
                this.graphics = new com.loracore.computer.jkernel.ClientSideGraphics(screenImage, net.minecraft.client.MinecraftClient.getInstance().getResourceManager());
            } else {
                // Для других игроков используем серверный рендеринг
                this.graphics = new com.loracore.computer.jkernel.ServerSideGraphics(tabletUuid);
            }
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




}