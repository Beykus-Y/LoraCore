// Полный исправленный файл: src/main/java/com/loracore/computer/VirtualMachine.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.api.*;
import com.loracore.computer.device.*;
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
import java.util.function.Consumer;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    private final IAsyncVFS vfs;
    private final UUID fsUuid;
    private final UUID tabletUuid; // <-- НОВОЕ ПОЛЕ
    private final Consumer<String> javaBootHandler; // <-- Новое поле
    private final List<Object> devices = new ArrayList<>();
    private final long startTime;
    private String crashMessage = null;

    private final Map<Integer, LuaThreadRunner> threads = new ConcurrentHashMap<>();
    private static final int MAIN_THREAD_ID = 0;
    private final int totalRamKb; // <-- НОВОЕ ПОЛЕ для хранения лимита памяти

    // ИСПРАВЛЕНО: Конструктор теперь принимает оба UUID и обработчик перезагрузки
    public VirtualMachine(String architecture, int totalRamKb, Terminal terminal, ResourceLoader resourceLoader, IAsyncVFS vfs, UUID fsUuid, UUID tabletUuid, Consumer<String> javaBootHandler) {
        this.totalRamKb = totalRamKb; // <-- СОХРАНЯЕМ лимит памяти
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfs = vfs;
        this.fsUuid = fsUuid;
        this.tabletUuid = tabletUuid; // <-- СОХРАНЯЕМ
        this.javaBootHandler = javaBootHandler; // <-- Сохраняем обработчик
        this.startTime = System.nanoTime();

        this.devices.add(new ThreadDevice(this));
        this.devices.add(new CpuDevice(this, architecture));
        this.devices.add(new RamDevice(this, totalRamKb));
        this.devices.add(new TerminalDevice(terminal));
        this.devices.add(new GpuDevice(this.tabletUuid)); // <-- ИСПРАВЛЕНО: Передаем UUID
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
        g.load(new OsAPI(this));
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
        if (bootScriptContent == null || bootScriptContent.isEmpty() || "nil".equals(bootScriptContent)) {
            final String error = "FATAL: Boot script is empty or could not be loaded.";
            LoraCoreMod.LOGGER.error(error);
            this.setCrashState(error);
            return;
        }
        // ИСПРАВЛЕНИЕ 2: Мы передаем null для Globals при создании раннера,
        // так как Globals теперь зависят от самого раннера.
        startNewLuaThread(MAIN_THREAD_ID, bootScriptContent, null);
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
        // ДИАГНОСТИКА: Логируем все события
        LoraCoreMod.LOGGER.info("VirtualMachine.pushEvent: {} args", args.length);
        for (int i = 0; i < args.length; i++) {
            LoraCoreMod.LOGGER.info("  arg[{}] = {} (type: {})", i, args[i], args[i] != null ? args[i].getClass().getSimpleName() : "null");
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
        
        LoraCoreMod.LOGGER.info("VirtualMachine: Pushing event to {} runners", threads.size());
        for (LuaThreadRunner runner : threads.values()) {
            LoraCoreMod.LOGGER.info("VirtualMachine: Pushing event to runner: {}", runner);
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
            this.javaBootHandler.accept(path);
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
}