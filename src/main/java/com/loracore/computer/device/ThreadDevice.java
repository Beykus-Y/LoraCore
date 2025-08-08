package com.loracore.computer.device;

import com.loracore.computer.VirtualMachine;
import com.loracore.computer.api.Callback;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadDevice {
    private final VirtualMachine vm;
    private final AtomicInteger nextThreadId = new AtomicInteger(1);

    public ThreadDevice(VirtualMachine vm) {
        this.vm = vm;
    }

    @Callback(value = "create", doc = "Creates and starts a new Lua thread from a code string.")
    public Object[] create(String code, Globals globals) {
        int threadId = nextThreadId.getAndIncrement();
        boolean success = vm.startNewLuaThread(threadId, code, null);
        if (success) {
            return new Object[]{threadId};
        } else {
            return new Object[]{null, "Failed to create thread."};
        }
    }

    @Callback(value = "send", doc = "Sends a message to a specific thread.")
    public void send(int threadId, Varargs args) {
        // Преобразуем Varargs в массив LuaValue для отправки
        LuaValue[] message = new LuaValue[args.narg() + 1];
        message[0] = LuaValue.valueOf("message"); // Имя события
        for (int i = 1; i <= args.narg(); i++) {
            message[i] = args.arg(i);
        }
        vm.pushEventToThread(threadId, message);
    }
}