// Полный исправленный файл: src/main/java/com/loracore/computer/api/FsAPI.java
package com.loracore.computer.api;

import com.loracore.computer.IVirtualFileSystem;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public class FsAPI extends LuaTable {

    // ИСПРАВЛЕНИЕ: Конструктор теперь принимает ТОЛЬКО реализацию VFS.
    public FsAPI(IVirtualFileSystem vfs) {
        // Каждая функция теперь - простая обертка над соответствующим методом VFS.
        // Никаких корутин и сложной логики.

        set("exists", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                // Прямой вызов, который будет ждать ответа (если реализация блокирующая)
                return vfs.exists(path.checkjstring());
            }
        });

        set("read", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                return vfs.read(path.checkjstring());
            }
        });

        set("write", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue path, LuaValue content) {
                return vfs.write(path.checkjstring(), content.checkjstring());
            }
        });

        set("makeDir", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                return vfs.makeDir(path.checkjstring());
            }
        });

        set("isDir", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                return vfs.isDir(path.checkjstring());
            }
        });

        set("list", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                return vfs.list(path.checkjstring());
            }
        });

        set("combine", new combine());
    }

    private static class combine extends TwoArgFunction {
        @Override
        public LuaValue call(LuaValue p1, LuaValue p2) {
            String path1 = p1.checkjstring();
            String path2 = p2.checkjstring();
            if (path1.endsWith("/") || path1.isEmpty()) {
                return valueOf(path1 + path2);
            }
            return valueOf(path1 + "/" + path2);
        }
    }
}