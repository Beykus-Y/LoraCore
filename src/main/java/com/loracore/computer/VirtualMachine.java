// Полный исправленный файл: src/main/java/com/loracore/computer/VirtualMachine.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import com.loracore.computer.api.*;
import com.loracore.computer.device.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualMachine {

    private final Terminal terminal;
    private final ResourceLoader resourceLoader;
    // ИСПРАВЛЕНИЕ: Убрали старый IVfsRequester. Теперь VM работает с простым интерфейсом.
    private final IVirtualFileSystem vfs;
    private final UUID fsUuid;
    private final List<Object> devices = new ArrayList<>();
    private final long startTime;
    private String crashMessage = null;

    private final Map<Integer, LuaThreadRunner> threads = new ConcurrentHashMap<>();
    private static final int MAIN_THREAD_ID = 0;

    private static class CustomPrint extends OneArgFunction {
        private final Terminal term;
        public CustomPrint(Terminal term) { this.term = term; }

        @Override
        public LuaValue call(LuaValue arg) {
            ClientApi.executeOnRenderThread(() -> term.print(arg.tojstring()));
            return LuaValue.NIL;
        }
    }

    // ИСПРАВЛЕНИЕ: Конструктор теперь принимает простую IVirtualFileSystem.
    public VirtualMachine(String architecture, int totalRamKb, Terminal terminal, ResourceLoader resourceLoader, IVirtualFileSystem vfs, UUID fsUuid) {
        this.terminal = terminal;
        this.resourceLoader = resourceLoader;
        this.vfs = vfs; // Присваиваем новую реализацию VFS
        this.fsUuid = fsUuid;
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
        g.set("require", new CustomRequire(g));

        // ИСПРАВЛЕНИЕ: FsAPI теперь создается с простой реализацией VFS.
        // Ему больше не нужен доступ к VM или Globals.
        if (this.vfs != null) {
            g.set("fs", new FsAPI(this.vfs));
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

    public void start(String bootScriptContent) {
        if (bootScriptContent == null || bootScriptContent.isEmpty() || "nil".equals(bootScriptContent)) {
            final String error = "FATAL: Boot script is empty or could not be loaded.";
            LoraCoreMod.LOGGER.error(error);
            this.setCrashState(error);
            return;
        }
        startNewLuaThread(MAIN_THREAD_ID, bootScriptContent);
    }

    // ИСПРАВЛЕНИЕ: Полностью удалены методы vfsRequest и resolveCallback.
    // Вся логика асинхронности и управления корутинами из VM убрана.

    public boolean startNewLuaThread(int threadId, String code) {
        if (threads.containsKey(threadId)) {
            return false;
        }
        try {
            Globals threadGlobals = createLuaGlobals();
            LuaThreadRunner runner = new LuaThreadRunner(this, threadGlobals, code);
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
    /**
     * Отправляет событие (массив LuaValue) в очередь конкретного потока по его ID.
     * @param threadId ID целевого потока.
     * @param event    Массив значений Lua, представляющий событие.
     */
    public void pushEventToThread(int threadId, LuaValue[] event) {
        LuaThreadRunner runner = threads.get(threadId);
        if (runner != null) {
            runner.pushEvent(event);
        }
    }

    // ... Остальные геттеры и методы без изменений ...
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
    // Вставьте этот код внутрь класса VirtualMachine

    private class CustomRequire extends VarArgFunction {
        private final Globals globals;

        public CustomRequire(Globals globals) {
            this.globals = globals;
        }

        @Override
        public Varargs invoke(Varargs args) {
            String path = args.checkjstring(1);

            // 1. Проверяем, был ли модуль уже загружен (стандартное поведение require)
            LuaValue loaded = globals.get("package").get("loaded").get(path);
            if (!loaded.isnil()) {
                return loaded;
            }

            // 2. Преобразуем путь модуля (например, "drivers.gpu") в путь к файлу ("drivers/gpu.lua")
            String filePath = path.replace('.', '/') + ".lua";

            // 3. Используем наш ResourceLoader для загрузки файла из ассетов мода
            String scriptContent = resourceLoader.load(filePath);

            if (scriptContent == null) {
                // 4. Если файл не найден, выбрасываем ошибку, как и стандартный require
                error("module '" + path + "' not found: " + path);
            }

            // 5. Компилируем и запускаем код модуля
            LuaValue chunk = globals.load(scriptContent, "@" + filePath);
            LuaValue result = chunk.call();

            // 6. Кэшируем результат, чтобы не загружать модуль дважды
            globals.get("package").get("loaded").set(path, result);

            return result;
        }
    }
}