// Полный исправленный файл: src/main/java/com/loracore/computer/VirtualMachine.java
package com.loracore.computer;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    private final IAsyncVFS vfs;
    private final UUID fsUuid;
    private final UUID tabletUuid; // <-- НОВОЕ ПОЛЕ
    private final BiConsumer<ServerPlayerEntity, String> javaBootHandler; // <-- Новое поле
    private final List<Object> devices = new ArrayList<>();
    private final long startTime;
    private String crashMessage = null;
    private volatile boolean isOn = false;
    private final ServerPlayerEntity player;
    public enum State { LUA, JAVA_KERNEL }
    private State currentState = State.LUA;

    private final Map<Integer, LuaThreadRunner> threads = new ConcurrentHashMap<>();
    private static final int MAIN_THREAD_ID = 0;
    private final int totalRamKb; // <-- НОВОЕ ПОЛЕ для хранения лимита памяти

    // ИСПРАВЛЕНО: Конструктор теперь принимает оба UUID и обработчик перезагрузки
    public VirtualMachine(ServerPlayerEntity player, String architecture, int totalRamKb, Terminal terminal, ResourceLoader resourceLoader, IAsyncVFS vfs, UUID fsUuid, UUID tabletUuid, BiConsumer<ServerPlayerEntity, String> javaBootHandler) {
        this.player = player;
        this.totalRamKb = totalRamKb; // <-- СОХРАНЯЕМ лимит памяти
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfs = vfs;
        this.fsUuid = fsUuid;
        this.tabletUuid = tabletUuid; // <-- СОХРАНЯЕМ
        this.javaBootHandler = javaBootHandler;// <-- Сохраняем обработчик
        this.startTime = System.nanoTime();


        this.devices.add(new ThreadDevice(this));
        this.devices.add(new CpuDevice(this, architecture));
        this.devices.add(new RamDevice(this, totalRamKb));
        this.devices.add(new TerminalDevice(terminal));
        this.devices.add(new GpuDevice(this.tabletUuid));
        this.devices.add(new RedstoneDevice());
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
        g.set("print", new CustomPrint(this.terminal));
        g.load(new OsAPI(this, this.player));
        g.set("bios", new BiosAPI(this));
        g.set("colors", new ColorsAPI());
        g.set("require", new CustomRequire(g));
        g.set("loadfile", new CustomLoadFile(g));

        if (this.vfs != null) {
            // Теперь мы передаем runner, который гарантированно не null
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

    public void start(String bootScriptContent) {
        // --- ИЗМЕНЕНИЕ: Устанавливаем флаг и запускаем поток ---
        if (isOn) {
            LoraCoreMod.LOGGER.warn("Попытка запустить уже работающую ВМ для планшета {}", this.tabletUuid);
            return;
        }
        if (bootScriptContent == null || bootScriptContent.isEmpty() || "nil".equals(bootScriptContent)) {
            // ...
            return;
        }
        this.isOn = true; // <--- Устанавливаем флаг
        this.crashMessage = null; // Сбрасываем старые ошибки
        startNewLuaThread(MAIN_THREAD_ID, bootScriptContent, null);
    }
    public UUID getTabletUuid() {
        return this.tabletUuid;
    }

    // ... (остальные методы без изменений: startNewLuaThread, pushEvent, shutdown, etc.)

    public boolean startNewLuaThread(int threadId, String code, Globals parentGlobals) {
        if (threads.containsKey(threadId)) return false;
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
        // --- ИЗМЕНЕНИЕ: Останавливаем потоки и сбрасываем флаг ---
        this.isOn = false; // <--- Сбрасываем флаг
        for (LuaThreadRunner runner : threads.values()) {
            runner.stop();
        }
        threads.clear();
    }

    public void reboot() {
        this.terminal.reboot();
    }

    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public double getUptime() {
        return (System.nanoTime() - startTime) / 1_000_000_000.0;
    }

    /**
     * Возвращает общий лимит памяти в килобайтах
     */
    public double getTotalRamKb() {
        return this.totalRamKb;
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