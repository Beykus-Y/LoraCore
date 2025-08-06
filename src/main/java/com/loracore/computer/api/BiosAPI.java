package com.loracore.computer.api;

import com.loracore.computer.VirtualMachine;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

public class BiosAPI extends LuaTable {
    private final VirtualMachine vm;

    public BiosAPI(VirtualMachine vm) {
        this.vm = vm;
        // Переименуйте и измените путь для ясности
        set("getRecovery", new getRecovery());
        set("getInstaller", new getInstaller());
    }

    private class getRecovery extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            // Загружайте правильный скрипт!
            String recoveryScript = vm.getResourceLoader().load("os/recovery.lua");
            return (recoveryScript != null) ? LuaValue.valueOf(recoveryScript) : LuaValue.NIL;
        }
    }
    private class getInstaller extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            String installerScript = vm.getResourceLoader().load("os/installer.txt");
            return (installerScript != null) ? LuaValue.valueOf(installerScript) : LuaValue.NIL;
        }
    }
}