// Расположение: src/main/java/com/loracore/computer/ServerVFSWrapper.java
package com.loracore.computer;

import org.luaj.vm2.LuaValue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронная обертка над синхронной серверной реализацией VFS.
 * Это позволяет серверной VirtualMachine использовать тот же асинхронный
 * интерфейс IAsyncVFS, что и клиентская, но с немедленным выполнением.
 */
public class ServerVFSWrapper implements IAsyncVFS {

    private final IFileSystem syncVfs;

    public ServerVFSWrapper(IFileSystem syncVfs) {
        this.syncVfs = syncVfs;
    }

    @Override
    public CompletableFuture<LuaValue> existsAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.exists(path) ? LuaValue.TRUE : LuaValue.FALSE);
    }

    @Override
    public CompletableFuture<LuaValue> readAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.read(path));
    }

    @Override
    public CompletableFuture<LuaValue> isDirectoryAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.isDirectory(path) ? LuaValue.TRUE : LuaValue.FALSE);
    }

    @Override
    public CompletableFuture<LuaValue> listAsync(String path) {
        String jsonList = syncVfs.list(path);
        return CompletableFuture.completedFuture(jsonList != null ? LuaValue.valueOf(jsonList) : LuaValue.NIL);
    }

    @Override
    public CompletableFuture<LuaValue> makeDirAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.makeDir(path) ? LuaValue.TRUE : LuaValue.FALSE);
    }

    @Override
    public CompletableFuture<LuaValue> writeAsync(String path, String content) {
        return CompletableFuture.completedFuture(syncVfs.write(path, content) ? LuaValue.TRUE : LuaValue.FALSE);
    }
}