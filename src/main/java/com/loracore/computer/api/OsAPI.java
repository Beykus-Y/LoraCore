package com.loracore.computer.api;

import com.loracore.computer.RebootSignalException;
import com.loracore.computer.VirtualMachine;
import com.loracore.computer.VirtualMachineManager;
import com.loracore.network.SwitchToClientKernelS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
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
    private final ServerPlayerEntity player;
    private static final Timer LUA_TIMER = new Timer("LoraCore-LuaTimers", true);
    private LuaValue coroutine_yield;

    public OsAPI(VirtualMachine vm, ServerPlayerEntity player) {
        this.vm = vm;
        this.player = player;
    }

    @Override
    public LuaValue call(LuaValue modname, LuaValue env) {
        this.coroutine_yield = env.get("coroutine").get("yield");

        LuaTable osTable = (LuaTable) env.get("os");
        osTable.set("reboot", new reboot(vm));
        osTable.set("shutdown", new shutdown(vm));
        osTable.set("sleep", new sleep(vm, coroutine_yield));
        osTable.set("pullEvent", new pullEvent(coroutine_yield));
        osTable.set("boot_java", new boot_java(vm)); // Передаем и vm, и player

        env.get("package").get("loaded").set("loracore_os", osTable);
        return osTable;
    }

    private static class reboot extends ZeroArgFunction {
        private final VirtualMachine vm;
        public reboot(VirtualMachine vm) { this.vm = vm; }
        @Override
        public LuaValue call() {
            VirtualMachineManager.getInstance().remove(vm.getTabletUuid());
            vm.reboot();
            throw new RebootSignalException();
        }
    }

    private static class shutdown extends ZeroArgFunction {
        private final VirtualMachine vm;
        public shutdown(VirtualMachine vm) { this.vm = vm; }
        @Override
        public LuaValue call() {
            vm.shutdown(); // Этот метод установит флаг isOn=false для сохранения
            throw new RebootSignalException();
        }
    }

    private static class sleep extends OneArgFunction {
        private final VirtualMachine vm;
        private final LuaValue yield;

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

            // Используем `invoke()` для Varargs, а не `call()`
            return yield.invoke(valueOf("timer")).arg1();
        }
    }

    private static class pullEvent extends VarArgFunction {
        private final LuaValue yield;
        public pullEvent(LuaValue yield) {
            this.yield = yield;
        }

        @Override
        public Varargs invoke(Varargs args) {
            return yield.invoke(args);
        }
    }

    // ИСПРАВЛЕННЫЙ КЛАСС
    private static class boot_java extends OneArgFunction {
        private final VirtualMachine vm;

        public boot_java(VirtualMachine vm) { this.vm = vm; }

        @Override
        public LuaValue call(LuaValue arg) {
            String path = arg.checkjstring();
            vm.bootJava(path); // <-- Просто вызываем метод ВМ
            throw new RebootSignalException();
        }
    }
}