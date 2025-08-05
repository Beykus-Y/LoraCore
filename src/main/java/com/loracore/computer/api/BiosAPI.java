package com.loracore.computer.api;

import com.loracore.computer.VirtualMachine;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

public class BiosAPI extends LuaTable {
    private final VirtualMachine vm;

    public BiosAPI(VirtualMachine vm) {
        this.vm = vm;
        set("getInstaller", new getInstaller());
    }

    private class getInstaller extends ZeroArgFunction {
        @Override
        public LuaValue call() {
            String installerScript = vm.getResourceLoader().load("os/installer.txt");
            return (installerScript != null) ? LuaValue.valueOf(installerScript) : LuaValue.NIL;
        }
    }
}