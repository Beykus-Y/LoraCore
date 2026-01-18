// Расположение: src/main/java/com/loracore/computer/ServerVFSWrapper.java
package com.loracore.computer;

import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.util.List;

public class ServerVFSWrapper implements IAsyncVFS {
    private final IFileSystem syncVfs;

    public ServerVFSWrapper(IFileSystem syncVfs) {
        this.syncVfs = syncVfs;
    }

    @Override
    public CompletableFuture<Boolean> existsAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.exists(path));
    }

    @Override
    public CompletableFuture<String> readAsync(String path) {
        try {
            return CompletableFuture.completedFuture(syncVfs.read(path));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Boolean> isDirectoryAsync(String path) {
        return CompletableFuture.completedFuture(syncVfs.isDirectory(path));
    }

    @Override
    public CompletableFuture<List<String>> listAsync(String path) {
        try {
            return CompletableFuture.completedFuture(syncVfs.list(path));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    @Override
    public CompletableFuture<Boolean> makeDirAsync(String path) {
        try {
            return CompletableFuture.completedFuture(syncVfs.makeDir(path));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Boolean> writeAsync(String path, String content) {
        try {
            return CompletableFuture.completedFuture(syncVfs.write(path, content));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<String> readBytesAsync(String path) {
        try {
            byte[] bytes = syncVfs.readBytes(path);
            return CompletableFuture.completedFuture(java.util.Base64.getEncoder().encodeToString(bytes));
        } catch (IOException e) {
            return CompletableFuture.completedFuture("");
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteAsync(String path) {
        try {
            return CompletableFuture.completedFuture(syncVfs.delete(path));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
