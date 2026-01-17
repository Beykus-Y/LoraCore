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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
     * Обновляет метрики системы, полученные с сервера.
     */
    public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, String tabletUuidStr, String fsUuidStr) {
        if (api instanceof KernelApiImpl apiImpl) {
            apiImpl.updateMetrics(cpuLoad, ramUsedKb, ramTotalKb, diskQueue, tabletUuidStr, fsUuidStr);
        }
    }
    
    /**
     * Возвращает строковое представление UUID планшета.
     */
    public String getTabletUuidStr() {
        if (api instanceof KernelApiImpl apiImpl) {
            return apiImpl.getTabletUuidStr();
        }
        return "N/A";
    }
    
    /**
     * Возвращает строковое представление UUID файловой системы.
     */
    public String getFsUuidStr() {
        if (api instanceof KernelApiImpl apiImpl) {
            return apiImpl.getFsUuidStr();
        }
        return "N/A";
    }

    /**
     * Распаковывает JAR файл и извлекает все файлы в карту.
     * Использует ZipInputStream для надежного чтения всех записей.
     * Handles potential IOException for individual entries without stopping the whole process.
     */
    private Map<String, byte[]> unpackJar(byte[] jarBytes) throws IOException {
        Map<String, byte[]> classData = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Ensure we skip directory entries
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                
                String entryName = entry.getName();
                // Log every entry found inside the JAR during boot process
                LoraCoreMod.LOGGER.info("Unpacking JAR entry: {}", entryName);
                
                try {
                    byte[] buffer = new byte[8192];
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    classData.put(entryName, baos.toByteArray());
                } catch (IOException e) {
                    // Handle potential IOException for individual entries without stopping the whole process
                    LoraCoreMod.LOGGER.warn("Failed to read JAR entry {}: {}. Skipping...", entryName, e.getMessage());
                } finally {
                    zis.closeEntry();
                }
            }
        }
        return classData;
    }

    /**
     * Полностью переписанный метод загрузки.
     * Использует unpackJar для надежного извлечения всех файлов, включая манифест.
     */
    public void boot(String jarPath) {
        api.getVfs().readBytes(jarPath).whenComplete((jarBytesOpt, error) -> {
            ClientApi.executeOnRenderThread(() -> {
                if (error != null) {
                    String errorMsg = error.getMessage();
                    setCrashState("VFS Error: Failed to read kernel at " + jarPath + (errorMsg != null ? ": " + errorMsg : ""));
                    return;
                }
                if (jarBytesOpt.isEmpty()) {
                    setCrashState("VFS Error: Failed to read kernel at " + jarPath);
                    return;
                }

                File tempJarFile = null;
                try {
                    byte[] jarBytes = jarBytesOpt.get();
                    
                    // Debug log: Check JAR bytes and ZIP magic header
                    String magicHeader = jarBytes.length > 4 
                        ? String.format("%02X %02X %02X %02X", jarBytes[0] & 0xFF, jarBytes[1] & 0xFF, jarBytes[2] & 0xFF, jarBytes[3] & 0xFF)
                        : "TOO SHORT";
                    LoraCoreMod.LOGGER.info("JAR bytes received. Size: {} bytes. First 4 bytes: {}", jarBytes.length, magicHeader);
                    
                    // Valid ZIP must start with 50 4B 03 04 (PK\x03\x04)
                    if (jarBytes.length < 4 || jarBytes[0] != 0x50 || jarBytes[1] != 0x4B || jarBytes[2] != 0x03 || jarBytes[3] != 0x04) {
                        throw new IOException("Invalid JAR file: Missing ZIP magic header (expected 50 4B 03 04, got " + magicHeader + ")");
                    }
                    
                    // 1. Распаковываем JAR и получаем все файлы
                    Map<String, byte[]> classData = unpackJar(jarBytes);

                    // 2. Case-insensitive and slash-agnostic manifest search using stream API
                    String manifestKey = classData.keySet().stream()
                        .filter(key -> {
                            String normalized = key.replace("\\", "/");
                            return normalized.equalsIgnoreCase("META-INF/MANIFEST.MF") || 
                                   normalized.endsWith("MANIFEST.MF");
                        })
                        .findFirst()
                        .orElse(null);
                    
                    byte[] manifestBytes = null;
                    if (manifestKey != null) {
                        manifestBytes = classData.get(manifestKey);
                        LoraCoreMod.LOGGER.info("Found manifest at entry: {}", manifestKey);
                    }
                    
                    if (manifestBytes == null) {
                        LoraCoreMod.LOGGER.error("Kernel JAR manifest not found. Available entries:");
                        for (String key : classData.keySet()) {
                            LoraCoreMod.LOGGER.error("  - {}", key);
                        }
                        throw new IOException("Kernel JAR is missing META-INF/MANIFEST.MF");
                    }

                    // 3. Создаем Manifest из байтов
                    Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
                    String mainClassName = manifest.getMainAttributes().getValue("Kernel-Main-Class");

                    if (mainClassName == null || mainClassName.trim().isEmpty()) {
                        // Better error message for missing Main Class
                        throw new IOException("Manifest found but 'Kernel-Main-Class' attribute is missing. Manifest location: " + manifestKey);
                    }

                    // 4. Создаем временный файл для ClassLoader
                    tempJarFile = File.createTempFile("loracore_kernel_", ".jar");
                    try (FileOutputStream fos = new FileOutputStream(tempJarFile)) {
                        fos.write(jarBytesOpt.get());
                    }

                    // 5. Получаем URL временного файла и создаем ClassLoader
                    URL[] urls = { tempJarFile.toURI().toURL() };
                    JarClassLoader kernelClassLoader = new JarClassLoader(urls, getClass().getClassLoader());

                    // 6. Загружаем главный класс ядра
                    Class<?> kernelClass = kernelClassLoader.loadClass(mainClassName);

                    if (!IKernel.class.isAssignableFrom(kernelClass)) {
                        throw new ClassCastException("Main class " + mainClassName + " does not implement IKernel.");
                    }

                    // 7. Создаем экземпляр и запускаем
                    this.kernelInstance = (IKernel) kernelClass.getConstructor().newInstance();
                    this.kernelInstance.onBoot(this.api);
                    this.isRunning = true;

                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Kernel Panic on boot", e);
                    
                    // Provide specific error messages for common issues
                    String errorMessage;
                    String exceptionMessage = e.getMessage();
                    if (exceptionMessage != null && exceptionMessage.contains("Kernel-Main-Class")) {
                        errorMessage = "Kernel Boot Error: Missing Main Class\n\n" +
                                "The kernel JAR manifest was found, but it is missing the required 'Kernel-Main-Class' attribute.\n\n" +
                                "Please ensure your kernel.jar has a valid MANIFEST.MF with:\n" +
                                "Kernel-Main-Class: <your.main.class.name>\n\n" +
                                "Error details: " + exceptionMessage;
                    } else if (exceptionMessage != null && exceptionMessage.contains("MANIFEST.MF")) {
                        errorMessage = "Kernel Boot Error: Missing Manifest\n\n" +
                                "The kernel JAR file does not contain a valid MANIFEST.MF file.\n\n" +
                                "Please ensure your kernel.jar includes META-INF/MANIFEST.MF with the required attributes.\n\n" +
                                "Error details: " + exceptionMessage;
                    } else {
                        errorMessage = "Kernel Panic: " + e.getClass().getSimpleName() + " - " + exceptionMessage;
                    }
                    
                    setCrashState(errorMessage);
                } finally {
                    // 8. Обязательно удаляем временный файл после использования
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


    private static class KernelApiImpl implements IKernelApi {
        private final TabletScreen parentScreen;
        private final IKernelVfs kernelVfs;
        private IKernelGraphics graphics;
        private final UUID tabletUuid;
        private final Map<Integer, CompletableFuture<Object[]>> pendingDeviceRequests = new ConcurrentHashMap<>();
        private final AtomicInteger nextRequestId = new AtomicInteger(0);
        
        // Метрики системы (обновляются с сервера)
        private volatile double cpuLoad = 0.0;
        private volatile double ramUsedKb = 0.0;
        private volatile double ramTotalKb = 2048.0;
        private volatile int diskQueue = 0;
        private volatile String tabletUuidStr = "N/A";
        private volatile String fsUuidStr = "N/A";

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

        @Override
        public Map<String, Double> getSystemMetrics() {
            // Возвращаем реальные метрики, полученные с сервера
            Map<String, Double> metrics = new HashMap<>();
            metrics.put("cpu_load", cpuLoad);
            metrics.put("ram_used_kb", ramUsedKb);
            metrics.put("ram_total_kb", ramTotalKb);
            metrics.put("disk_queue", (double) diskQueue);
            // Добавляем UUID как строки для AboutApp
            metrics.put("tablet_uuid_str", Double.NaN); // Используем специальное значение
            metrics.put("fs_uuid_str", Double.NaN);
            return metrics;
        }
        
        /**
         * Обновляет метрики системы, полученные с сервера.
         */
        public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, String tabletUuidStr, String fsUuidStr) {
            this.cpuLoad = cpuLoad;
            this.ramUsedKb = ramUsedKb;
            this.ramTotalKb = ramTotalKb;
            this.diskQueue = diskQueue;
            this.tabletUuidStr = tabletUuidStr;
            this.fsUuidStr = fsUuidStr;
        }
        
        @Override
        public String getTabletUuidStr() {
            return tabletUuidStr;
        }
        
        @Override
        public String getFsUuidStr() {
            return fsUuidStr;
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
                    
                    // Nuclear Base64 sanitization: remove EVERY character that is not a valid Base64 symbol
                    // This sanitization is applied to the ENTIRE assembled string (for large files assembled from chunks)
                    // ensuring that even if chunks contained invalid characters, the final decoded JAR will be valid
                    String cleanBase64 = base64Data.replaceAll("[^A-Za-z0-9+/=]", "");
                    LoraCoreMod.LOGGER.debug("Decoding Base64 data from path: {} (original length: {}, cleaned length: {})", path, base64Data.length(), cleanBase64.length());
                    return Optional.of(Base64.getDecoder().decode(cleanBase64));
                } catch (RuntimeException e) {
                    // Re-throw CPU cycle errors
                    throw e;
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
                    if (jsonData == null || jsonData.trim().isEmpty()) {
                        return List.of();
                    }
                    
                    // Проверяем, является ли jsonData JSON массивом
                    String trimmed = jsonData.trim();
                    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                        // Это не JSON массив, возвращаем пустой список
                        LoraCoreMod.LOGGER.warn("list() received non-array JSON from path: {} - data: {}", path, trimmed);
                        return List.of();
                    }
                    
                    // Parse JSON array
                    String[] items = GSON.fromJson(jsonData, String[].class);
                    if (items == null) {
                        return List.of();
                    }
                    return List.of(items);
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Failed to parse list data from path: {}", path, e);
                    return List.of();
                }
            });
        }
    }
}