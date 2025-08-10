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

    private final WorldStorageVFS blockingVfs;

    public ServerVFSWrapper(UUID fsUuid) {
        // Мы не можем получить worldSavePath напрямую здесь.
        // Эту логику нужно будет централизовать в VirtualFileSystemManager.
        // Пока что передаем null и исправим это при рефакторинге VFSManager.
        this.blockingVfs = new WorldStorageVFS(VirtualFileSystemManager.getInstance().getWorldSavePath(), fsUuid);
    }

    @Override
    public CompletableFuture<LuaValue> existsAsync(String path) {
        return CompletableFuture.completedFuture(
                blockingVfs.exists(path) ? LuaValue.TRUE : LuaValue.FALSE
        );
    }

    @Override
    public CompletableFuture<LuaValue> readAsync(String path) {
        return CompletableFuture.completedFuture(blockingVfs.read(path));
    }

    @Override
    public CompletableFuture<LuaValue> isDirectoryAsync(String path) {
        return CompletableFuture.completedFuture(
                blockingVfs.isDirectory(path) ? LuaValue.TRUE : LuaValue.FALSE
        );
    }

    @Override
    public CompletableFuture<LuaValue> listAsync(String path) {
        String jsonList = blockingVfs.list(path);
        return CompletableFuture.completedFuture(
                jsonList != null ? LuaValue.valueOf(jsonList) : LuaValue.NIL
        );
    }

    // Операции записи пока не нужны для асинхронного интерфейса, но добавим их для полноты
    @Override
    public CompletableFuture<LuaValue> makeDirAsync(String path) {
        return CompletableFuture.completedFuture(
                blockingVfs.makeDir(path) ? LuaValue.TRUE : LuaValue.FALSE
        );
    }

    @Override
    public CompletableFuture<LuaValue> writeAsync(String path, String content) {
        return CompletableFuture.completedFuture(
                blockingVfs.write(path, content) ? LuaValue.TRUE : LuaValue.FALSE
        );
    }
}