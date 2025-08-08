// Новый файл: src/main/java/com/loracore/computer/IAsyncVFS.java
package com.loracore.computer;

import org.luaj.vm2.LuaValue;
import java.util.concurrent.CompletableFuture;

/**
 * Асинхронный интерфейс для VFS, который может быть реализован на клиенте.
 * Находится в общем коде, чтобы VirtualMachine могла с ним работать.
 */
public interface IAsyncVFS {
    CompletableFuture<LuaValue> existsAsync(String path);
    CompletableFuture<LuaValue> readAsync(String path);
    CompletableFuture<LuaValue> isDirectoryAsync(String path);
    CompletableFuture<LuaValue> listAsync(String path);
    CompletableFuture<LuaValue> makeDirAsync(String path);
    CompletableFuture<LuaValue> writeAsync(String path, String content);
}