// Полный исправленный файл: src/main/java/com/loracore/computer/api/OsAPI.java
package com.loracore.computer.api;

import com.loracore.computer.RebootSignalException;
import com.loracore.computer.VirtualMachine;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.Timer;
import java.util.TimerTask;

public class OsAPI extends LibFunction {
    private final VirtualMachine vm;
    private static final Timer LUA_TIMER = new Timer("LoraCore-LuaTimers", true);
    // ИСПРАВЛЕНИЕ 1: Переменная для хранения ссылки на библиотеку 'coroutine'
    private LuaValue coroutine_yield;

    public OsAPI(VirtualMachine vm) {
        this.vm = vm;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        // ИСПРАВЛЕНИЕ 2: Получаем и сохраняем функцию 'yield' из библиотеки 'coroutine'
        // Это нужно сделать один раз при инициализации.
        this.coroutine_yield = env.get("coroutine").get("yield");

        LuaTable osTable = (LuaTable) env.get("os");
        osTable.set("reboot", new reboot(vm));
        osTable.set("sleep", new sleep(vm, coroutine_yield)); // Передаем 'yield' в конструктор
        osTable.set("pullEvent", new pullEvent(coroutine_yield)); // Передаем 'yield' в конструктор

        env.get("package").get("loaded").set("loracore_os", osTable);
        return osTable;
    }

    private static class reboot extends ZeroArgFunction {
        private final VirtualMachine vm;
        public reboot(VirtualMachine vm) { this.vm = vm; }
        @Override
        public LuaValue call() {
            vm.reboot();
            throw new RebootSignalException();
        }
    }

    private static class sleep extends OneArgFunction {
        private final VirtualMachine vm;
        private final LuaValue yield; // Храним ссылку на функцию yield

        // Конструктор теперь принимает LuaValue
        public sleep(VirtualMachine vm, LuaValue yield) {
            this.vm = vm;
            this.yield = yield;
        }

        @Override
        public LuaValue call(LuaValue arg) {
            double seconds = arg.checkdouble();
            LUA_TIMER.schedule(new TimerTask() {
                @Override public void run() { vm.pushEvent("timer"); }
            }, (long)(seconds * 1000));

            // ИСПРАВЛЕНИЕ 3: Вызываем сохраненную функцию yield
            return yield.call(valueOf("timer"));
        }
    }

    private static class pullEvent extends VarArgFunction {
        private final LuaValue yield; // Храним ссылку на функцию yield

        // Конструктор теперь не нуждается в 'vm' и убирает предупреждения
        public pullEvent(LuaValue yield) {
            this.yield = yield;
        }

        @Override
        public Varargs invoke(Varargs args) {
            // ИСПРАВЛЕНИЕ 3: Вызываем сохраненную функцию yield
            return yield.invoke(args);
        }
    }
}