// Файл: src/main/java/com/loracore/computer/VirtualMachine.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.api.*;
import com.loracore.computer.device.*;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    // ИЗМЕНЕНИЕ: Зависимость от IVfsRequester вместо конкретной реализации
    private final IVfsRequester vfsRequester;
    private final UUID fsUuid; // Храним UUID здесь
    private final List<Object> devices = new ArrayList<>();
    private final long startTime;

    private final Map<Integer, LuaThreadRunner> threads = new ConcurrentHashMap<>();
    private static final int MAIN_THREAD_ID = 0;

    private final Map<Integer, LuaThread> waitingCoroutines = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(1);

    private static class CustomPrint extends OneArgFunction {
        private final Terminal term;
        public CustomPrint(Terminal term) { this.term = term; }

        @Override
        public LuaValue call(LuaValue arg) {
            ClientApi.executeOnRenderThread(() -> term.print(arg.tojstring()));
            return LuaValue.NIL;
        }
    }

    // ИЗМЕНЕНИЕ: Конструктор теперь принимает IVfsRequester и UUID
    public VirtualMachine(String architecture, int totalRamKb, Terminal terminal, ResourceLoader resourceLoader, IVfsRequester vfsRequester, UUID fsUuid) {
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfsRequester = vfsRequester; // Сохраняем Requester
        this.fsUuid = fsUuid;             // Сохраняем UUID
        this.startTime = System.nanoTime();

        this.devices.add(new ThreadDevice(this));
        this.devices.add(new CpuDevice(this, architecture));
        this.devices.add(new RamDevice(this, totalRamKb));
        this.devices.add(new TerminalDevice(terminal));
        this.devices.add(new GpuDevice(terminal));
    }

    private Globals createLuaGlobals() {
        Globals g = JsePlatform.standardGlobals();
        g.set("print", new CustomPrint(this.terminal));
        g.load(new OsAPI(this));
        g.set("bios", new BiosAPI(this));
        g.set("colors", new ColorsAPI());

        // ИСПРАВЛЕНО: Убрана передача Globals в конструктор FsAPI, как вы и предложили
        if (this.vfsRequester != null) {
            g.set("fs", new FsAPI(this, g));
        }

        LuaTable tabletApi = new LuaTable();
        for (Object device : this.devices) {
            LuaTable api = LuaApiHelper.createApi(device);
            String deviceName = device.getClass().getSimpleName().replace("Device", "").toLowerCase();
            tabletApi.set(deviceName, api);
        }
        g.set("tablet", tabletApi);

        return g;
    }

    // ИЗМЕНЕНИЕ: Метод boot теперь называется start и принимает готовый код
    public void start(String bootScriptContent) {
        if (bootScriptContent == null || bootScriptContent.isEmpty()) {
            LoraCoreMod.LOGGER.error("Boot script is empty!");
            terminal.showCrashScreen("FATAL: Boot script is empty or could not be loaded.");
            return;
        }
        startNewLuaThread(MAIN_THREAD_ID, bootScriptContent);
    }

    public Varargs vfsRequest(LuaThread coroutine, com.loracore.network.vfs.VfsRequestC2SPacket.Operation op, Varargs args) {
        if (vfsRequester == null) {
            throw new LuaError("VFS is not available.");
        }
        int callbackId = nextCallbackId.getAndIncrement();
        waitingCoroutines.put(callbackId, coroutine);

        String path = args.checkjstring(1);
        String content = args.optjstring(2, "");
        LoraCoreMod.LOGGER.info(
                "[VFS REQUEST] Preparing for Lua yield. CallbackID: {}, Operation: {}, Path: {}, FS_UUID: {}",
                callbackId,
                op,
                path,
                this.fsUuid
        );
        vfsRequester.sendRequest(callbackId, this.fsUuid, op, path, content);

        // ИСПРАВЛЕНИЕ: Используем статический метод LuaThread.yield(), чтобы приостановить корутину из Java
        return LuaValue.NIL;
    }

    public void resolveCallback(int callbackId, VfsResponseS2CPacket.ResponseType type, String data) {
        LuaThread coroutine = waitingCoroutines.remove(callbackId);
        if (coroutine != null) {
            LuaValue responseValue = switch (type) {
                case TRUE -> LuaValue.TRUE;
                case FALSE -> LuaValue.FALSE;
                case STRING, TABLE_JSON -> LuaValue.valueOf(data);
                default -> LuaValue.NIL;
            };

            LuaThreadRunner runner = threads.get(MAIN_THREAD_ID);
            if (runner != null) {
                LoraCoreMod.LOGGER.info(
                        "[VFS RESPONSE] Queuing coroutine for resumption. CallbackID: {}, Value: {}",
                        callbackId,
                        responseValue.tojstring() // .tojstring() для читаемого вывода
                );
                // Возобновляем корутину с полученным результатом
                runner.resumeWith(coroutine, responseValue);
            }
        }
    }

    // ... Остальные методы (startNewLuaThread, pushEvent, shutdown и т.д.) без изменений ...
    public boolean startNewLuaThread(int threadId, String code) {
        if (threads.containsKey(threadId)) {
            return false;
        }
        try {
            Globals threadGlobals = createLuaGlobals();
            LuaThreadRunner runner = new LuaThreadRunner(threadGlobals, this.terminal, code);
            threads.put(threadId, runner);
            runner.start();
            return true;
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Failed to start Lua thread {}", threadId, e);
            return false;
        }
    }

    public void pushEventToThread(int threadId, LuaValue[] event) {
        LuaThreadRunner runner = threads.get(threadId);
        if (runner != null) {
            runner.pushEvent(event);
        }
    }

    public void pushEvent(Object... args) {
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
        for (LuaThreadRunner runner : threads.values()) {
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

    public double getMemoryUsage() {
        LuaThreadRunner mainRunner = threads.get(MAIN_THREAD_ID);
        if (mainRunner != null) {
            return mainRunner.getGlobals().get("collectgarbage").call("count").todouble();
        }
        return 0;
    }
}