// Полностью исправленный файл: src/main/java/com/loracore/computer/SyncVFS.java
package com.loracore.computer;

import org.luaj.vm2.LuaValue;

/**
 * Простая, синхронная реализация VFS, которая теперь зависит от интерфейса IBlockingVFS.
 */
public class SyncVFS implements IVirtualFileSystem {

    // ИСПРАВЛЕНИЕ: Зависим от интерфейса, а не от конкретного клиентского класса.
    private final IBlockingVFS blockingVfs;

    // ИСПРАВЛЕНИЕ: Конструктор теперь публичный и принимает интерфейс.
    public SyncVFS(IBlockingVFS blockingVfs) {
        this.blockingVfs = blockingVfs;
    }

    @Override
    public LuaValue read(String path) {
        return blockingVfs.readBlocking(path);
    }

    @Override
    public LuaValue exists(String path) {
        return blockingVfs.existsBlocking(path);
    }

    @Override
    public LuaValue write(String path, String content) {
        return blockingVfs.writeBlocking(path, content);
    }

    @Override
    public LuaValue makeDir(String path) {
        return blockingVfs.makeDirBlocking(path);
    }

    @Override
    public LuaValue isDir(String path) {
        return blockingVfs.isDirBlocking(path);
    }

    @Override
    public LuaValue list(String path) {
        return blockingVfs.listBlocking(path);
    }

    // Примечание: У нас нет метода delete в IVirtualFileSystem, поэтому пока его не реализуем.
    // Если он понадобится, его нужно будет добавить в оба интерфейса (IVFS и IBlockingVFS) и реализовать.
}