package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.lang.LoraCompiler;
import com.loracore.lang.TextAssembler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.Map;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    private final IAsyncVFS vfs;
    private final UUID fsUuid;
    private final UUID tabletUuid;
    private final long startTime;
    private long tickCount = 0; // Счетчик тиков вместо System.nanoTime()
    private String crashMessage = null;
    private volatile boolean isOn = false;
    private final ServerPlayerEntity player;

    private final int totalRamKb; // <-- НОВОЕ ПОЛЕ для хранения лимита памяти

    // === HARDWARE LAYER: Низкоуровневая эмуляция железа ===
    private final SystemBus systemBus;
    private final GenericRam systemRam;
    private final VirtualCpu virtualCpu;
    private final GpuMmioDevice gpuMmioDevice;
    
    // Instruction Budget для синхронного выполнения
    private long cycleBudget = 0;
    private final CpuTiers.Config cpuConfig;
    
    // Boot Grace Period: первые 15 секунд после старта - бесплатный VFS
    private long bootGracePeriodEnd = 0;
    
    // Task Queue: операции ставятся в очередь и выполняются, когда есть циклы
    private final Queue<Runnable> taskQueue = new LinkedList<>();
    
    // System Metrics Tracking (volatile for thread safety)
    private volatile double cpuLoad = 0.0;
    private volatile double ramUsedKb = 0.0;
    private volatile double ramTotalKb = 0.0;
    private volatile int diskQueue = 0;
    private long cyclesConsumedInPeriod = 0;
    private int ticksInPeriod = 0;

    // ИСПРАВЛЕНО: Конструктор теперь принимает оба UUID и обработчик перезагрузки
    public VirtualMachine(ServerPlayerEntity player, String architecture, int totalRamKb,
                          Terminal terminal, ResourceLoader resourceLoader, IAsyncVFS vfs,
                          UUID fsUuid, UUID tabletUuid,
                          // Новые аргументы:
                          SystemBus systemBus, GenericRam systemRam, GpuMmioDevice gpuMmioDevice) {

        this.player = player;
        this.totalRamKb = totalRamKb;
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfs = vfs;
        this.fsUuid = fsUuid;
        this.tabletUuid = tabletUuid;
        this.startTime = System.nanoTime();
        this.tickCount = 0;

        // Получаем конфигурацию процессора
        this.cpuConfig = CpuTiers.getConfigForArchitecture(architecture);

        // === ИНИЦИАЛИЗАЦИЯ HARDWARE LAYER ===
        // Теперь мы используем переданные компоненты, которые уже настроены менеджером
        this.systemBus = systemBus;
        this.systemRam = systemRam;
        this.gpuMmioDevice = gpuMmioDevice;

        // Виртуальный процессор создается здесь, так как он зависит от шины и конфига
        // Seed генерируется из UUID планшета (как и было)
        long cpuSeed = tabletUuid.getMostSignificantBits() ^ tabletUuid.getLeastSignificantBits();
        this.virtualCpu = new VirtualCpu(this.systemBus, this.systemRam, this.cpuConfig, cpuSeed);

        LoraCoreMod.LOGGER.info("[VM] VirtualMachine initialized for {} (Arch: {})", tabletUuid, architecture);
    }
    public boolean isOn() {
        return this.isOn;
    }

    public void start(String sourceCode) {
        try {
            // 1. Компилируем LoraC -> ASM (String)
            LoraCompiler compiler = new LoraCompiler();
            String asmCode = compiler.compile(sourceCode);

            LoraCoreMod.LOGGER.debug("[VM] Compiled ASM:\n{}", asmCode);

            // 2. Ассемблируем ASM -> Machine Code (byte[])
            TextAssembler assembler = new TextAssembler();
            byte[] programBytes = assembler.compile(asmCode);

            // 3. Загружаем бинарник
            start(programBytes);

        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("[VM] Compilation failed", e);
            setCrashState("Compilation Error: " + e.getMessage());
        }
    }
    public void start(byte[] programBytes) {
        if (programBytes == null) {
            setCrashState("No program loaded");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int previewLen = Math.min(16, programBytes.length);
        for(int i=0; i<previewLen; i++) {
            sb.append(String.format("%02X ", programBytes[i]));
        }
        LoraCoreMod.LOGGER.info("[VM DEBUG] Loading {} bytes. First 16 bytes: [ {}]", programBytes.length, sb.toString());

        int maxLen = Math.min(programBytes.length, systemRam.getSize());
        for (int i = 0; i < maxLen; i++) {
            systemRam.write(i, programBytes[i]);
        }
        virtualCpu.reset();
        this.isOn = true;
        this.crashMessage = null;
        this.cycleBudget = 50000;
        this.bootGracePeriodEnd = System.currentTimeMillis() + 15000;
    }
    public UUID getTabletUuid() {
        return this.tabletUuid;
    }

    public UUID getFsUuid() {
        return this.fsUuid;
    }

    public void shutdown() {
        // Set isOn to false IMMEDIATELY when shutdown() is called to prevent race conditions
        this.isOn = false;
        terminal.clear();
    }

    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public double getUptime() {
        // Используем счетчик тиков вместо System.nanoTime()
        // 20 TPS = 1 секунда, поэтому tickCount / 20.0 = секунды
        return tickCount / 20.0;
    }

    /**
     * Вызывается каждый серверный тик для обработки Instruction Budget.
     * Заменяет потоки на синхронное выполнение в рамках игрового тика.
     */
    public void tick() {
        if (!isOn || crashMessage != null) {
            return;
        }

        // Увеличиваем счетчик тиков
        tickCount++;

        try {
            // Вычисляем сгенерированные циклы за этот тик
            long generated = cpuConfig.targetFreq / 20;
            
            // Добавляем бюджет инструкций
            cycleBudget += generated;
            
            // Ограничиваем максимальный бюджет для предотвращения лагов
            // Cap at 2x target frequency to prevent infinite growth when idle
            if (cycleBudget > cpuConfig.targetFreq * 2) {
                cycleBudget = cpuConfig.targetFreq * 2;
            }
            
            // Clamp cycleBudget to prevent infinite catch-up lags if it becomes negative
            if (cycleBudget < -5000) {
                cycleBudget = -5000;
            }
            
            // Обрабатываем очередь задач: каждая задача стоит 100 циклов
            while (!taskQueue.isEmpty() && consumeCycles(100)) {
                Runnable task = taskQueue.poll();
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LoraCoreMod.LOGGER.error("[VM] Task execution failed:", e);
                    }
                }
            }
            
            // Обновляем метрики каждые 20 тиков (1 секунда)
            ticksInPeriod++;
            if (ticksInPeriod >= 20) {
                cpuLoad = (double) cyclesConsumedInPeriod / cpuConfig.targetFreq;
                if (cpuLoad > 1.0) cpuLoad = 1.0;
                double maxUsage = getSystemRamUsage();
                ramUsedKb = maxUsage;
                ramTotalKb = (double) totalRamKb;
                diskQueue = taskQueue.size();
                if (maxUsage > totalRamKb) {
                    setCrashState("Out of memory: used " + maxUsage + "KB of " + totalRamKb + "KB");
                    this.isOn = false;
                }
                cyclesConsumedInPeriod = 0;
                ticksInPeriod = 0;
            }

            // Выполняем инструкции CPU
            while (cycleBudget > 0 && isOn) {
                // Проверяем, что процессор не выполняет пустую память
                // Если PC выходит за пределы RAM, останавливаем выполнение
                if (virtualCpu.pc < 0 || virtualCpu.pc >= systemRam.getSize()) {
                    LoraCoreMod.LOGGER.warn("[VM] CPU PC out of bounds: 0x{}, halting execution",
                            String.format("%06X", virtualCpu.pc));
                    break;
                }
                
                // Выполняем одну инструкцию CPU
                int spent = virtualCpu.step();
                
                // Обновляем устройства шины
                systemBus.tickDevices(spent);
                
                // Уменьшаем бюджет на количество потраченных тактов
                cycleBudget -= spent;
                
                // Проверяем физику процессора (нагрев, bit flips)
                // Это уже делается внутри VirtualCpu.step()
            }

        } catch (HardwareInterruptException e) {
            // Аппаратное прерывание - переводим систему в Kernel Panic
            LoraCoreMod.LOGGER.error("[VM] Hardware Interrupt: {}", e.getMessage());
            setCrashState("Kernel Panic: " + e.getMessage());
            this.isOn = false;
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("[VM] Unexpected error in tick():", e);
            setCrashState("System Error: " + e.getMessage());
        }
    }

    /**
     * Возвращает общий лимит памяти в килобайтах
     */
    public double getTotalRamKb() {
        return this.totalRamKb;
    }

    private double getTotalRamUsageKb() {
        return getSystemRamUsage();
    }

    /**
     * Возвращает реальное использование системной RAM в килобайтах.
     * Подсчитывает количество ненулевых байтов в systemRam.
     */
    public double getSystemRamUsage() {
        if (systemRam == null) {
            return 0.0;
        }
        
        byte[] ram = systemRam.getRawMemory();
        if (ram == null) {
            return 0.0;
        }
        
        // Подсчитываем ненулевые байты (упрощенная метрика использования)
        int nonZeroBytes = 0;
        for (byte b : ram) {
            if (b != 0) {
                nonZeroBytes++;
            }
        }
        
        // Возвращаем в килобайтах
        return nonZeroBytes / 1024.0;
    }

    /**
     * Потребляет такты из бюджета. Используется Lua и Java-приложениями.
     * @param cycles Количество тактов для потребления
     * @param cycles Количество тактов для потребления
     * @return true, если такты были успешно потреблены, false если бюджет закончился
     */
    public synchronized boolean consumeCycles(long cycles) {
        // Boot grace period: первые 15 секунд после старта - все операции бесплатны
        if (System.currentTimeMillis() < bootGracePeriodEnd) {
            // Все равно отслеживаем циклы для метрик, даже в grace period
            cyclesConsumedInPeriod += cycles;
            return true;
        }
        
        // Обновляем счетчик потребленных циклов для метрик
        cyclesConsumedInPeriod += cycles;
        
        if (cycleBudget >= cycles) {
            cycleBudget -= cycles;
            return true;
        }
        return false;
    }
    
    /**
     * Возвращает текущие метрики для отправки на клиент.
     * Должен вызываться каждые 20 тиков (1 секунда).
     * @return SystemMetrics с текущими метриками или null, если метрики еще не готовы
     */
    public synchronized SystemMetrics getMetricsForClient() {
        if (ticksInPeriod == 0 && cpuLoad == 0.0 && ramUsedKb == 0.0) {
            return null;
        }

        return new SystemMetrics(
                cpuLoad,
                ramUsedKb,
                ramTotalKb,
                diskQueue,
                virtualCpu.pc, // <--- БЕРЕМ ТЕКУЩИЙ PC ИЗ CPU
                tabletUuid.toString(),
                fsUuid.toString()
        );
    }
    
    /**
     * Record для передачи метрик на клиент.
     */
    public record SystemMetrics(
            double cpuLoad,
            double ramUsedKb,
            double ramTotalKb,
            int diskQueue,
            int currentPc, // <--- НОВОЕ ПОЛЕ
            String tabletUuidStr,
            String fsUuidStr
    ) {}

    /**
     * Возвращает текущий бюджет тактов (для отладки).
     */
    public synchronized long getCycleBudget() {
        return cycleBudget;
    }
    
    /**
     * Добавляет задачу в очередь для выполнения.
     * Задача будет выполнена, когда у VM будет достаточно циклов (100 за задачу).
     * @param task Задача для выполнения
     */
    public synchronized void enqueueTask(Runnable task) {
        if (isOn && task != null) {
            taskQueue.offer(task);
        }
    }
    
    /**
     * Возвращает текущие метрики системы.
     * @return Map с метриками: cpu_load, ram_used_kb, ram_total_kb, disk_queue
     */
    public synchronized Map<String, Double> getSystemMetrics() {
        Map<String, Double> metrics = new java.util.HashMap<>();
        
        // CPU Load: cyclesSpentInLastSecond / targetFreqPerSecond
        // Уже вычисляется в tick() каждые 20 тиков (1 секунда)
        metrics.put("cpu_load", cpuLoad);
        
        double totalRamUsed = getTotalRamUsageKb();
        metrics.put("ram_used_kb", totalRamUsed);
        metrics.put("ram_total_kb", (double) totalRamKb);
        
        // Disk Queue: размер очереди задач
        metrics.put("disk_queue", (double) taskQueue.size());
        
        return metrics;
    }

    public void setCrashState(String message) {
        this.crashMessage = message;
    }

    public boolean isRunning() {
        return this.isOn && this.crashMessage == null;
    }

    public String getCrashMessage() {
        return this.crashMessage;
    }

    /**
     * Сериализует базовое состояние ВМ (включена/выключена).
     */
    public NbtCompound writeToNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("isOn", this.isOn);
        // В будущем здесь можно сохранять и другие простые данные, например, ник игрока
        return nbt;
    }

    /**
     * Загружает базовое состояние ВМ.
     * @param nbt Данные для загрузки.
     */
    public void readFromNbt(NbtCompound nbt) {
        this.isOn = false;
    }

    public void pushEvent(String type, int keyCode) {}
    public void pushEvent(String type, String text) {}
    public void pushEvent(String type, double x, double y, int button) {}

    public java.util.List<Object> getDevices() {
        return java.util.List.of();
    }

    public double getMemoryUsage() {
        return 0.0;
    }
}
