package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.kernel.*;
import com.loracore.gui.TabletScreen;
import com.loracore.network.InvokeDeviceMethodC2SPacket;
import com.loracore.lang.Assembler; // Импорт вашего нового Ассемблера
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Менеджер управления эмуляцией на стороне клиента.
 * Отвечает за загрузку ROM/BIOS, инициализацию виртуальной машины
 * и связь с периферией.
 */
public class KernelManager {

    private final IKernelApi api;
    private final TabletScreen parentScreen;

    // Вместо IKernel (Java) здесь будет жить экземпляр эмулятора
    // private VirtualMachine vm; 

    private boolean isRunning = false;
    private String crashError = null;

    public KernelManager(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
        this.parentScreen = parentScreen;
        // API остается как мост к аппаратным ресурсам (VFS, GPU, Сеть)
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
            apiImpl.recreateGraphics(newScreenImage);
            // Если эмулятор имеет GPU компонент, здесь нужно обновить ссылку на экран
        }
    }

    public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, long uptimeSeconds, String tabletUuidStr, String fsUuidStr) {
        if (api instanceof KernelApiImpl apiImpl) {
            apiImpl.updateMetrics(cpuLoad, ramUsedKb, ramTotalKb, diskQueue, uptimeSeconds, tabletUuidStr, fsUuidStr);
        }
    }

    public String getTabletUuidStr() {
        if (api instanceof KernelApiImpl apiImpl) {
            return apiImpl.getTabletUuidStr();
        }
        return "N/A";
    }

    public String getFsUuidStr() {
        if (api instanceof KernelApiImpl apiImpl) {
            return apiImpl.getFsUuidStr();
        }
        return "N/A";
    }

    /**
     * Загружает код в эмулятор.
     * Поддерживает raw бинарники и компиляцию ASM на лету.
     */
    public void boot(String bootPath) {
        // 1. Загружаем файл из VFS
        api.getVfs().readBytes(bootPath).whenComplete((bytesOpt, error) -> {
            ClientApi.executeOnRenderThread(() -> {
                if (error != null) {
                    setCrashState("Boot Error: " + error.getMessage());
                    return;
                }
                if (bytesOpt.isEmpty()) {
                    setCrashState("Boot Error: Kernel/BIOS file not found: " + bootPath);
                    return;
                }

                try {
                    byte[] rawData = bytesOpt.get();
                    byte[] executableCode;

                    // 2. Проверяем тип файла
                    if (bootPath.endsWith(".asm") || bootPath.endsWith(".lora")) {
                        // Это исходный код - компилируем
                        String sourceCode = new String(rawData, StandardCharsets.UTF_8);
                        LoraCoreMod.LOGGER.info("Compiling assembly from {}...", bootPath);

                        // Используем Assembler из com.loracore.lang
                        Assembler assembler = new Assembler();
                        // Предполагаем, что у вас есть метод compile или parse в Assembler
                        // Если нет, его нужно добавить. Пока что это псевдокод логики:
                        // executableCode = assembler.compile(sourceCode); 

                        // ВРЕМЕННО: Если метод compile еще не реализован, кидаем ошибку
                        // throw new UnsupportedOperationException("Runtime ASM compilation not ready");

                        // ВРЕМЕННО: Просто переводим текст в байты для теста (НЕ ДЛЯ ПРОДАКШЕНА)
                        executableCode = rawData;

                    } else {
                        // Это бинарный образ (ROM)
                        executableCode = rawData;
                        LoraCoreMod.LOGGER.info("Loading binary image ({} bytes)...", executableCode.length);
                    }

                    // 3. Инициализация VM (Эмулятора)
                    // Здесь мы должны создать VirtualMachine и загрузить в неё executableCode.
                    // Так как VirtualMachine сейчас серверная, здесь оставляем логику
                    // готовности к запуску.

                    // this.vm = new VirtualMachine(...);
                    // this.vm.loadMemory(0, executableCode);
                    // this.vm.cpu.reset();

                    LoraCoreMod.LOGGER.info("Boot sequence completed. System running.");
                    this.isRunning = true;
                    this.crashError = null;

                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Boot failure", e);
                    setCrashState("Boot Failed: " + e.getMessage());
                }
            });
        });
    }

    public void render(int mouseX, int mouseY, float delta) {
        if (!isRunning) return;
        // Здесь вызываем рендер эмулятора (например, GPU отрисовку)
        // if (vm != null) vm.render(mouseX, mouseY, delta);
    }

    public void tick() {
        if (isRunning) {
            try {
                // Здесь вызываем такт эмулятора
                // if (vm != null) vm.tick();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("CPU Runtime Error", e);
                setCrashState("CPU Fault: " + e.getMessage());
            }
        }
    }

    public void onEvent(KernelEvent event) {
        if (isRunning) {
            try {
                // Передаем события ввода в порты ввода-вывода эмулятора
                // if (vm != null) vm.pushEvent(event);
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Input Error", e);
            }
        }
    }

    public void shutdown() {
        if (isRunning) {
            // if (vm != null) vm.shutdown();
            LoraCoreMod.LOGGER.info("System halted.");
        }
        this.isRunning = false;
        this.crashError = null;
    }

    public void setLuaExecutor(java.util.function.Consumer<String> executor) {}

    private void setCrashState(String message) {
        this.crashError = message;
        this.isRunning = false;
        LoraCoreMod.LOGGER.error("Kernel crashed: {}", message);
    }

    /**
     * Обработка ответа от аппаратного устройства.
     */
    public void onDeviceResult(int requestId, boolean success, Object[] result) {
        if (this.api instanceof KernelApiImpl apiImpl) {
            apiImpl.onDeviceResult(requestId, success, result);
        }
    }

    // --- Внутренняя реализация API для связи с железом ---
    private static class KernelApiImpl implements IKernelApi {
        private final TabletScreen parentScreen;
        private final IKernelVfs kernelVfs;
        private IKernelGraphics graphics;
        private final UUID tabletUuid;
        private final Map<Integer, CompletableFuture<Object[]>> pendingDeviceRequests = new ConcurrentHashMap<>();
        private final AtomicInteger nextRequestId = new AtomicInteger(0);

        private volatile double cpuLoad = 0.0;
        private volatile double ramUsedKb = 0.0;
        private volatile double ramTotalKb = 2048.0;
        private volatile int diskQueue = 0;
        private volatile long uptimeSeconds = 0L;
        private volatile String tabletUuidStr = "N/A";
        private volatile String fsUuidStr = "N/A";

        public KernelApiImpl(ClientVFS vfs, UUID tabletUuid, TabletScreen parentScreen, net.minecraft.client.texture.NativeImage screenImage, boolean isOwner) {
            this.parentScreen = parentScreen;
            this.kernelVfs = new KernelVfsImpl(vfs);
            this.tabletUuid = tabletUuid;
            // Инициализация графики (ServerSideGraphics отправляет пакеты на сервер)
            this.graphics = new com.loracore.computer.jkernel.ServerSideGraphics(tabletUuid);
        }

        public void recreateGraphics(net.minecraft.client.texture.NativeImage newScreenImage) {
            this.graphics = new com.loracore.computer.jkernel.ServerSideGraphics(this.tabletUuid);
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
            Map<String, Double> metrics = new HashMap<>();
            metrics.put("cpu_load", cpuLoad);
            metrics.put("ram_used_kb", ramUsedKb);
            metrics.put("ram_total_kb", ramTotalKb);
            metrics.put("disk_queue", (double) diskQueue);
            metrics.put("uptime", (double) uptimeSeconds);
            return metrics;
        }

        public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, long uptimeSeconds, String tabletUuidStr, String fsUuidStr) {
            this.cpuLoad = cpuLoad;
            this.ramUsedKb = ramUsedKb;
            this.ramTotalKb = ramTotalKb;
            this.diskQueue = diskQueue;
            this.uptimeSeconds = uptimeSeconds;
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

        public KernelVfsImpl(ClientVFS vfs) {
            this.vfs = vfs;
        }

        @Override public CompletableFuture<Boolean> exists(String path) { return vfs.existsAsync(path); }
        @Override public CompletableFuture<Boolean> isDirectory(String path) { return vfs.isDirectoryAsync(path); }

        @Override
        public CompletableFuture<Optional<byte[]>> readBytes(String path) {
            return vfs.readBytesAsync(path).thenApply(base64Data -> {
                if (base64Data == null || base64Data.isEmpty()) return Optional.empty();
                try {
                    String cleanBase64 = base64Data.replaceAll("[^A-Za-z0-9+/=]", "");
                    return Optional.of(java.util.Base64.getDecoder().decode(cleanBase64));
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Failed to decode base64 data from path: {}", path, e);
                    return Optional.empty();
                }
            });
        }

        @Override public CompletableFuture<Boolean> writeBytes(String path, byte[] data) { return vfs.writeAsync(path, java.util.Base64.getEncoder().encodeToString(data)); }
        @Override public CompletableFuture<Boolean> makeDir(String path) { return vfs.makeDirAsync(path); }
        @Override public CompletableFuture<Boolean> delete(String path) { return vfs.deleteAsync(path); }

        @Override
        public CompletableFuture<List<String>> list(String path) {
            // Исправление: vfs.listAsync уже возвращает распаршенный List<String>,
            // поэтому нам не нужно снова парсить JSON.
            return vfs.listAsync(path);
        }
    }
}