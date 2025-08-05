// Файл: src/main/java/com/loracore/computer/api/FsAPI.java
package com.loracore.computer.api;

import com.loracore.computer.VirtualMachine;
import com.loracore.network.vfs.VfsRequestC2SPacket.Operation;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

public class FsAPI extends LuaTable {

    // ИСПРАВЛЕНО: Конструктор снова принимает и VM, и Globals
    public FsAPI(VirtualMachine vm, Globals g) {
        super();

        class VfsFunction extends VarArgFunction {
            private final Operation op;
            VfsFunction(Operation op) { this.op = op; }

            @Override
            public Varargs invoke(Varargs args) {
                // ИСПРАВЛЕНО: Получаем текущую корутину из переданного объекта Globals
                LuaValue coroutine = g.running;
                if (!coroutine.isthread()) {
                    error("VFS operations must be called from within a coroutine.");
                }
                // Передаем корутину в VM, а VM вернет NIL. Yield должен быть в Lua.
                return vm.vfsRequest(coroutine.checkthread(), op, args);
            }
        }

        set("exists",  new VfsFunction(Operation.EXISTS));
        set("read",    new VfsFunction(Operation.READ));
        set("write",   new VfsFunction(Operation.WRITE));
        set("makeDir", new VfsFunction(Operation.MAKEDIR));
        set("isDir",   new VfsFunction(Operation.ISDIR));
        set("list",    new VfsFunction(Operation.LIST));
        set("combine", new combine());
    }

    private static class combine extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue p1, LuaValue p2) {
            String path1 = p1.checkjstring();
            String path2 = p2.checkjstring();
            if (path1.endsWith("/")) {
                return valueOf(path1 + path2);
            }
            return valueOf(path1 + "/" + path2);
        }
    }
}