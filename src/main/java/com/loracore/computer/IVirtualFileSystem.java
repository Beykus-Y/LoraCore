// Файл: src/main/java/com/loracore/computer/IVirtualFileSystem.java
package com.loracore.computer;

import org.luaj.vm2.LuaValue;

// Этот интерфейс описывает операции, которые может выполнять Lua API.
// Он находится в общем коде и не зависит ни от клиента, ни от сервера.
public interface IVirtualFileSystem {
    LuaValue exists(String path);
    LuaValue read(String path);
    LuaValue write(String path, String content);
    LuaValue makeDir(String path);
    LuaValue isDir(String path);
    LuaValue list(String path);
}