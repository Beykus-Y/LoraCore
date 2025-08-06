// Новый файл: src/main/java/com/loracore/computer/IBlockingVFS.java
package com.loracore.computer;

import org.luaj.vm2.LuaValue;

/**
 * Интерфейс, описывающий контракт для блокирующей (синхронной) файловой системы.
 * Находится в 'main', поэтому может использоваться общим кодом, таким как SyncVFS.
 */
public interface IBlockingVFS {

    LuaValue readBlocking(String path);
    LuaValue existsBlocking(String path);
    LuaValue writeBlocking(String path, String content);
    LuaValue makeDirBlocking(String path);
    LuaValue isDirBlocking(String path);
    LuaValue deleteBlocking(String path);
    LuaValue listBlocking(String path);

}