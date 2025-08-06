// Полный исправленный файл: src/client/java/com/loracore/computer/KernelManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.computer.kernel.IKernel;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelVfs;
import com.loracore.computer.kernel.JarClassLoader;
import com.loracore.computer.kernel.KernelEvent;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public KernelManager(ClientVFS vfs, TabletScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.api = new KernelApiImpl(vfs, parentScreen);
        this.classLoader = new JarClassLoader(getClass().getClassLoader());
    }
    public boolean isRunning() {
        return this.isRunning;
    }

    public String getCrashMessage() {
        return this.crashError;
    }

    public void boot(String jarPath) {
        api.getVfs().readBytes(jarPath).whenComplete((jarBytesOpt, error) -> {
            parentScreen.getClient().execute(() -> {
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
                    e.printStackTrace();
                    setCrashState("Kernel Panic: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            });
        });
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (crashError != null) {
            int width = context.getScaledWindowWidth();
            int height = context.getScaledWindowHeight();
            context.fill(0, 0, width, height, 0xCC_AA0000);
            context.drawCenteredTextWithShadow(parentScreen.getTextRenderer(), "KERNEL PANIC", width / 2, height / 2 - 20, 0xFFFFFFFF);
            parentScreen.getTextRenderer().wrapLines(Text.literal(crashError), width - 20).forEach((line) -> {
                context.drawCenteredTextWithShadow(parentScreen.getTextRenderer(), line, width / 2, height / 2, 0xFFFFFFFF);
            });
            return;
        }
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onRender(context, mouseX, mouseY, delta);
            } catch (Exception e) {
                e.printStackTrace();
                setCrashState("Render thread crashed: " + e.getMessage());
            }
        }
    }

    public void tick() {
        if (isRunning && kernelInstance != null) {
            try {
                kernelInstance.onTick();
            } catch (Exception e) {
                e.printStackTrace();
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
                e.printStackTrace();
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
            while ((entry = jarStream.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), jarStream.readAllBytes());
                }
            }
        }
        return result;
    }

    private static class KernelApiImpl implements IKernelApi {
        private final ClientVFS vfs;
        private final TabletScreen parentScreen;
        private final KernelVfsImpl kernelVfs;

        public KernelApiImpl(ClientVFS vfs, TabletScreen parentScreen) {
            this.vfs = vfs;
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
        }

        @Override
        public IKernelVfs getVfs() {
            return this.kernelVfs;
        }

        @Override
        public int runLuaScript(String path) {
            return -1;
        }

        @Override
        public void sendToLua(int threadId, Object... message) {
        }

        @Override
        public int[] getTerminalSize() {
            final int FONT_HEIGHT = 9;
            final int FONT_WIDTH = 6;
            int termWidth = (parentScreen.getTabletWidth() - 8) / FONT_WIDTH;
            int termHeight = (parentScreen.getTabletHeight() - 8) / FONT_HEIGHT;
            return new int[]{termWidth, termHeight};
        }

        @Override
        public void reboot() {
            parentScreen.reboot();
        }

        @Override
        public void shutdown() {
            parentScreen.close();
        }
    }

    private static class KernelVfsImpl implements IKernelVfs {
        private final ClientVFS vfs;
        private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

        public KernelVfsImpl(ClientVFS vfs) { this.vfs = vfs; }

        @Override
        public CompletableFuture<Boolean> exists(String path) {
            return vfs.existsAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean());
        }

        @Override
        public CompletableFuture<Boolean> isDirectory(String path) {
            return vfs.isDirectoryAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean());
        }

        @Override
        public CompletableFuture<Optional<byte[]>> readBytes(String path) {
            return vfs.readAsync(path).thenApply(luaValue -> {
                if (luaValue.isnil() || !luaValue.isstring()) {
                    return Optional.empty();
                }
                return Optional.of(luaValue.tojstring().getBytes(StandardCharsets.UTF_8));
            });
        }

        @Override
        public CompletableFuture<Boolean> writeBytes(String path, byte[] data) {
            String content = new String(data, StandardCharsets.UTF_8);
            return vfs.writeAsync(path, content).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean());
        }

        @Override
        public CompletableFuture<Boolean> makeDir(String path) {
            return vfs.makeDirAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean());
        }

        @Override
        public CompletableFuture<Boolean> delete(String path) {
            return vfs.deleteAsync(path).thenApply(luaValue -> !luaValue.isnil() && luaValue.toboolean());
        }

        @Override
        public CompletableFuture<List<String>> list(String path) {
            return vfs.listAsync(path).thenApply(luaValue -> {
                if (luaValue.isnil() || !luaValue.isstring()) {
                    return List.of();
                }
                try {
                    // Сервер возвращает JSON-массив строк, парсим его
                    return GSON.fromJson(luaValue.tojstring(), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                } catch (Exception e) {
                    // В случае ошибки парсинга возвращаем пустой список
                    return List.of();
                }
            });
        }
    }
}