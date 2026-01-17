// Полный исправленный файл: src/main/java/com/loracore/computer/VirtualMachine.java
package com.loracore.computer;

import com.google.common.reflect.TypeToken;
import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.api.*;
import com.loracore.computer.device.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.terraformersmc.modmenu.ModMenu.GSON;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    private final IAsyncVFS vfs;
    private final UUID fsUuid;
    private final UUID tabletUuid; // <-- НОВОЕ ПОЛЕ
    private final BiConsumer<ServerPlayerEntity, String> javaBootHandler; // <-- Новое поле
    private final List<Object> devices = new ArrayList<>();
    private final long startTime;
    private long tickCount = 0; // Счетчик тиков вместо System.nanoTime()
    private String crashMessage = null;
    private volatile boolean isOn = false;
    private final ServerPlayerEntity player;
    public enum State { LUA, JAVA_KERNEL }
    private State currentState = State.LUA;

    private final Map<Integer, LuaThreadRunner> threads = new ConcurrentHashMap<>();
    private static final int MAIN_THREAD_ID = 0;

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
    public VirtualMachine(ServerPlayerEntity player, String architecture, int totalRamKb, Terminal terminal, ResourceLoader resourceLoader, IAsyncVFS vfs, UUID fsUuid, UUID tabletUuid, BiConsumer<ServerPlayerEntity, String> javaBootHandler) {
        this.player = player;
        this.totalRamKb = totalRamKb;
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfs = vfs; // Теперь типы совпадают
        this.fsUuid = fsUuid;
        this.tabletUuid = tabletUuid;
        this.javaBootHandler = javaBootHandler;
        this.startTime = System.nanoTime(); // Оставляем для совместимости, но используем tickCount
        this.tickCount = 0;

        // Получаем конфигурацию процессора
        this.cpuConfig = CpuTiers.getConfigForArchitecture(architecture);

        // === ИНИЦИАЛИЗАЦИЯ HARDWARE LAYER ===
        // 1. Создаем системную шину
        this.systemBus = new SystemBus();
        
        // 2. Создаем системную RAM (2MB = 0x000000 - 0x1FFFFF)
        int ramSizeBytes = totalRamKb * 1024;
        this.systemRam = new GenericRam(ramSizeBytes);
        this.systemBus.mapDevice(0x000000, this.systemRam);
        
        // 3. Создаем GPU MMIO устройство (VRAM: 0x400000 - 0x47A120)
        // Используем ленивую инициализацию: экран будет создан при первом обращении
        // Пока создаем временный экран, он будет заменен при первом вызове getOrCreateScreen()
        ServerScreenState screenState = new ServerScreenState();
        this.gpuMmioDevice = new GpuMmioDevice(screenState);
        this.systemBus.mapDevice(0x400000, this.gpuMmioDevice);
        
        // 4. Создаем виртуальный процессор
        long cpuSeed = tabletUuid.getMostSignificantBits() ^ tabletUuid.getLeastSignificantBits();
        this.virtualCpu = new VirtualCpu(this.systemBus, this.systemRam, this.cpuConfig, cpuSeed);

        // Старые устройства (для обратной совместимости)
        this.devices.add(new ThreadDevice(this));
        this.devices.add(new CpuDevice(this, architecture));
        this.devices.add(new RamDevice(this, totalRamKb));
        this.devices.add(new TerminalDevice(terminal));
        this.devices.add(new GpuDevice(this.tabletUuid));
        this.devices.add(new RedstoneDevice());
        this.devices.add(new DiskManagerDevice());
        this.devices.add(new MotherboardDevice());
    }
    public boolean isOn() {
        return this.isOn;
    }

    private static class CustomPrint extends OneArgFunction {
        private final Terminal term;
        public CustomPrint(Terminal term) { this.term = term; }
        @Override
        public LuaValue call(LuaValue arg) {
            ClientApi.executeOnRenderThread(() -> term.print(arg.tojstring()));
            return LuaValue.NIL;
        }
    }

    private Globals createLuaGlobals(LuaThreadRunner runner) {
        Globals g = JsePlatform.standardGlobals();
        // Explicitly load DebugLib to ensure the sethook mechanism works
        g.load(new org.luaj.vm2.lib.DebugLib());
        g.set("print", new CustomPrint(this.terminal));
        g.load(new OsAPI(this, this.player));
        g.set("bios", new BiosAPI(this));
        g.set("colors", new ColorsAPI());
        g.set("require", new CustomRequire(g));
        g.set("loadfile", new CustomLoadFile(g));

        if (this.vfs != null) {
            // Теперь эта строка корректна, так как FsAPI ожидает IAsyncVFS
            g.set("fs", new FsAPI(runner, this.vfs));
        }

        // Добавляем API term для терминала
        LuaTable termApi = new LuaTable();
        termApi.set("write", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                ClientApi.executeOnRenderThread(() -> terminal.print(arg.tojstring()));
                return LuaValue.NIL;
            }
        });
        termApi.set("read", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(terminal.read());
            }
        });
        termApi.set("clear", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                ClientApi.executeOnRenderThread(terminal::clear);
                return LuaValue.NIL;
            }
        });
        g.set("term", termApi);

        LuaTable tabletApi = new LuaTable();
        for (Object device : this.devices) {
            LuaTable api = LuaApiHelper.createApi(device);
            String deviceName = device.getClass().getSimpleName().replace("Device", "").toLowerCase();
            tabletApi.set(deviceName, api);
        }
        g.set("tablet", tabletApi);

        return g;
    }
    public boolean isMainThreadAlive() {
        LuaThreadRunner mainRunner = threads.get(MAIN_THREAD_ID);
        return mainRunner != null && mainRunner.isAlive();
    }

    public void start(String bootScriptContent) {
        // ✅ ИСПРАВЛЕНИЕ: Проверяем, жив ли ГЛАВНЫЙ ПОТОК, а не просто флаг.
        // Это позволяет перезапустить машину после загрузки мира из сохранения.
        if (isMainThreadAlive()) {
            LoraCoreMod.LOGGER.warn("Попытка запустить уже работающую ВМ для планшета {}", this.tabletUuid);
            return;
        }

        if (bootScriptContent == null || bootScriptContent.isEmpty() || "nil".equals(bootScriptContent)) {
            LoraCoreMod.LOGGER.error("Невозможно запустить ВМ {}: пустой или некорректный загрузочный скрипт.", this.tabletUuid);
            setCrashState("BIOS content is nil or empty.");
            return;
        }

        // Ensure isOn is set to true BEFORE starting the Lua thread to prevent race conditions
        this.isOn = true;
        this.crashMessage = null;
        // Grant initial "kickstart" budget for BIOS and initial filesystem requests
        this.cycleBudget = 50000;
        // Set boot grace period: 15 seconds of free VFS operations
        this.bootGracePeriodEnd = System.currentTimeMillis() + 15000;
        // Now start the thread - isOn is already true, so pushEvent checks will pass
        startNewLuaThread(MAIN_THREAD_ID, bootScriptContent, null);
    }
    public UUID getTabletUuid() {
        return this.tabletUuid;
    }

    public UUID getFsUuid() {
        return this.fsUuid;
    }

    // ... (остальные методы без изменений: startNewLuaThread, pushEvent, shutdown, etc.)

    public boolean startNewLuaThread(int threadId, String code, Globals parentGlobals) {
        if (threads.containsKey(threadId)) return false;
        
        // Ensure isOn is true before starting the thread to prevent race conditions
        if (!isOn) {
            LoraCoreMod.LOGGER.warn("Attempted to start Lua thread {} but VM is not on", threadId);
            return false;
        }
        
        try {
            // ИСПРАВЛЕНИЕ 3: Переработан порядок инициализации
            // Сначала создаем раннер
            LuaThreadRunner runner = new LuaThreadRunner(this, null, code);
            // Затем создаем Globals, передавая в них уже созданный раннер
            Globals threadGlobals = (parentGlobals != null) ? parentGlobals : createLuaGlobals(runner);
            // Устанавливаем Globals в раннер
            runner.setGlobals(threadGlobals);

            threads.put(threadId, runner);
            runner.start();
            return true;
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Failed to start Lua thread {}", threadId, e);
            setCrashState("Failed to start Lua thread: " + e.getMessage());
            return false;
        }
    }

    public void pushEvent(Object... args) {
        // Guard clause: prevent events from being pushed after shutdown
        if (!isOn) return;
        
        // Логирование изменено на DEBUG уровень
        if (LoraCoreMod.LOGGER.isDebugEnabled()) {
            LoraCoreMod.LOGGER.debug("VirtualMachine.pushEvent: {} args", args.length);
            for (int i = 0; i < args.length; i++) {
                LoraCoreMod.LOGGER.debug("  arg[{}] = {} (type: {})", i, args[i], args[i] != null ? args[i].getClass().getSimpleName() : "null");
            }
        }

        LuaValue[] event = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) {
            event[i] = switch (args[i]) {
                case String s -> LuaValue.valueOf(s);
                case Integer num -> LuaValue.valueOf(num);
                case Double num -> LuaValue.valueOf(num);
                case Boolean bool -> LuaValue.valueOf(bool);
                default -> LuaValue.NIL;
            };
        }

        LoraCoreMod.LOGGER.debug("VirtualMachine: Pushing event to {} runners", threads.size());
        for (LuaThreadRunner runner : threads.values()) {
            LoraCoreMod.LOGGER.debug("VirtualMachine: Pushing event to runner: {}", runner);
            runner.pushEvent(event);
        }
    }

    public void pushEventToThread(int threadId, LuaValue[] event) {
        LuaThreadRunner runner = threads.get(threadId);
        if (runner != null) {
            runner.pushEvent(event);
        }
    }

    public void shutdown() {
        // Set isOn to false IMMEDIATELY when shutdown() is called to prevent race conditions
        this.isOn = false;
        
        // Stop all running threads
        for (LuaThreadRunner runner : threads.values()) {
            runner.stop();
        }
        
        // Explicitly clear the threads map after stopping them to ensure no late events can be processed
        threads.clear();
    }

    public void reboot() {
        this.terminal.reboot();
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
            // Это делает слабый CPU медленнее, но не ломает систему
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
                // Ограничиваем нагрузку до 1.0 (100%)
                if (cpuLoad > 1.0) cpuLoad = 1.0;
                
                // Обновляем RAM метрики
                ramUsedKb = getMemoryUsage();
                ramTotalKb = (double) totalRamKb;
                diskQueue = taskQueue.size();
                
                // Сбрасываем счетчики
                cyclesConsumedInPeriod = 0;
                ticksInPeriod = 0;
            }

            // ИНТЕЛЛЕКТУАЛЬНЫЙ ТИК: Если работает Lua или Java-ядро, НЕ вызываем CPU
            if (currentState == State.JAVA_KERNEL || threads.containsKey(MAIN_THREAD_ID)) {
                // Бюджет тратится только на устройства шины (GPU и т.д.), но не на CPU
                // Lua и Java будут потреблять бюджет через consumeCycles()
                while (cycleBudget > 0 && isOn) {
                    systemBus.tickDevices(1);
                    cycleBudget -= 1;
                }
                return;
            }

            // Выполняем инструкции CPU только если нет активных Lua/Java потоков
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
        // Метрики обновляются каждые 20 тиков в tick(), поэтому просто возвращаем их
        // Проверяем, что метрики были обновлены хотя бы раз
        if (ticksInPeriod == 0 && cpuLoad == 0.0 && ramUsedKb == 0.0) {
            // Метрики еще не были обновлены, возвращаем null
            return null;
        }
        
        return new SystemMetrics(
            cpuLoad,
            ramUsedKb,
            ramTotalKb,
            diskQueue,
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
        
        // RAM: комбинируем systemRam usage + Lua GC count
        double systemRamUsage = getSystemRamUsage();
        double luaMemoryUsage = getMemoryUsage();
        // Используем максимум из двух, так как они могут перекрываться
        double totalRamUsed = Math.max(systemRamUsage, luaMemoryUsage);
        metrics.put("ram_used_kb", totalRamUsed);
        metrics.put("ram_total_kb", (double) totalRamKb);
        
        // Disk Queue: размер очереди задач
        metrics.put("disk_queue", (double) taskQueue.size());
        
        return metrics;
    }

    public State getCurrentState() {
        return this.currentState;
    }

    public double getMemoryUsage() {
        LuaThreadRunner mainRunner = threads.get(MAIN_THREAD_ID);
        if (mainRunner != null) {
            try {
                Globals globals = mainRunner.getGlobals();
                if (globals != null) {
                    LuaValue collectgarbage = globals.get("collectgarbage");
                    if (collectgarbage != null && !collectgarbage.isnil()) {
                        LuaValue result = collectgarbage.call("count");
                        if (result != null && !result.isnil()) {
                            double usage = result.todouble();
                            
                            // ИСПРАВЛЕНИЕ: Дополнительная защита от абсурдных значений
                            // collectgarbage("count") может возвращать очень большие числа
                            if (usage > 1000000) { // Если больше 1GB, что явно нереально
                                LoraCoreMod.LOGGER.warn("Unrealistic memory usage reported: {}KB, returning safe value", (int)usage);
                                return 100.0; // Возвращаем безопасное значение 100 KB
                            }
                            
                            return usage;
                        }
                    }
                }
            } catch (Exception e) {
                // Если что-то пошло не так, возвращаем безопасное значение
                LoraCoreMod.LOGGER.warn("Error getting memory usage: " + e.getMessage());
                return 0.0;
            }
        }
        return 0.0;
    }

    public void setCrashState(String message) {
        this.crashMessage = message;
    }

    public boolean isRunning() {
        LuaThreadRunner mainRunner = threads.get(MAIN_THREAD_ID);
        return mainRunner != null && mainRunner.isAlive() && this.crashMessage == null;
    }

    public String getCrashMessage() {
        return this.crashMessage;
    }

    /**
     * Вызывается из Lua API для перезагрузки в Java-режим
     */
    public void bootJava(String path) {
        if (this.javaBootHandler != null) {
            this.currentState = State.JAVA_KERNEL; // Меняем состояние!
            LoraCoreMod.LOGGER.info("VM {} переходит в состояние JAVA_KERNEL.", this.tabletUuid);
            this.javaBootHandler.accept(this.player, path);
        }
    }

    /**
     * Возвращает список устройств для использования в LuaThreadRunner
     */
    public List<Object> getDevices() {
        return devices;
    }

    private class CustomRequire extends VarArgFunction {
        private final Globals globals;
        public CustomRequire(Globals globals) { this.globals = globals; }

        @Override
        public Varargs invoke(Varargs args) {
            String path = args.checkjstring(1);
            LuaValue loaded = globals.get("package").get("loaded").get(path);
            if (!loaded.isnil()) return loaded;
            String filePath = path.replace('.', '/') + ".lua";
            String scriptContent = resourceLoader.load(filePath);
            if (scriptContent == null) error("module '" + path + "' not found: " + path);
            LuaValue chunk = globals.load(scriptContent, "@" + filePath);
            LuaValue result = chunk.call();
            globals.get("package").get("loaded").set(path, result);
            return result;
        }
    }

    private class CustomLoadFile extends OneArgFunction {
        private final Globals globals;
        public CustomLoadFile(Globals globals) { this.globals = globals; }

        @Override
        public LuaValue call(LuaValue arg) {
            String filePath = arg.checkjstring();
            String scriptContent = resourceLoader.load(filePath);
            if (scriptContent == null) {
                return NIL; // loadfile возвращает nil при ошибке
            }
            try {
                LuaValue chunk = globals.load(scriptContent, "@" + filePath);
                return chunk;
            } catch (Exception e) {
                return NIL; // loadfile возвращает nil при ошибке
            }
        }
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
        this.isOn = nbt.getBoolean("isOn");
    }
}