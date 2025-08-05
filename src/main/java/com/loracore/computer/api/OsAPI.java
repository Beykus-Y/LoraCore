// [ИСПРАВЛЕНО]
package com.loracore.computer.api;

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

/**
 * Предоставляет Lua-скриптам базовые функции операционной системы,
 * такие как перезагрузка и обработка событий.
 * Регистрируется как глобальная таблица 'os'.
 */
public class OsAPI extends LibFunction {
    private final VirtualMachine vm;
    private static final Timer timer = new Timer("LoraCore-LuaTimers", true);
    // [ИЗМЕНЕНО] Конструктор теперь публичный
    public OsAPI(VirtualMachine vm) {
        this.vm = vm;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        LuaTable os = new LuaTable();
        os.set("reboot", new reboot(vm));
        os.set("sleep", new sleep(vm));
        env.set("os", os);
        return os;
    }

    // Внутренние классы остаются без изменений
    private static class reboot extends ZeroArgFunction {
        private final VirtualMachine vm;
        public reboot(VirtualMachine vm) { this.vm = vm; }
        @Override
        public LuaValue call() {
            vm.reboot();
            return NIL;
        }
    }
    private static class sleep extends OneArgFunction {
        private final VirtualMachine vm;
        public sleep(VirtualMachine vm) { this.vm = vm; }

        @Override
        public LuaValue call(LuaValue arg) {
            double seconds = arg.checkdouble();
            long millis = (long)(seconds * 1000);

            // Создаем событие "timer", которое будет отправлено в VM через X миллисекунд
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Отправляем событие в основной поток VM
                    vm.pushEvent("timer");
                }
            }, millis);

            // Теперь в Lua нужно дождаться этого события
            return NIL;
        }
    }
    private static class pullEvent extends VarArgFunction {
        private final VirtualMachine vm;
        public pullEvent(VirtualMachine vm) { this.vm = vm; }

        @Override
        public Varargs invoke(Varargs args) {
            // Теперь эта функция ничего не делает в Java.
            // Она просто существует, чтобы `os.pullEvent` был доступен.
            // Вся магия будет происходить в Lua через `coroutine.yield`.
            return LuaValue.varargsOf(new LuaValue[]{
                    LuaValue.valueOf("yield_placeholder")
            });
        }
    }
}