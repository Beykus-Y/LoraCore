package com.loracore.computer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAsyncVFS {
    CompletableFuture<Boolean> existsAsync(String path);
    CompletableFuture<String> readAsync(String path);
    CompletableFuture<Boolean> isDirectoryAsync(String path);
    CompletableFuture<List<String>> listAsync(String path);
    CompletableFuture<Boolean> makeDirAsync(String path);
    CompletableFuture<Boolean> writeAsync(String path, String content);
    CompletableFuture<Boolean> deleteAsync(String path);
    CompletableFuture<String> readBytesAsync(String path);
}
