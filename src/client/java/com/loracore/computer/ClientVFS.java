// Полный и завершенный файл: src/client/java/com/loracore/computer/ClientVFS.java
package com.loracore.computer;

import com.loracore.network.vfs.VfsRequestC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.luaj.vm2.LuaValue;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// ИСПРАВЛЕНИЕ: Класс теперь реализует и старый IVfsRequester, и новый IBlockingVFS.
public class ClientVFS implements IVfsRequester, IBlockingVFS {
    private final UUID fsUuid;
    private final ConcurrentHashMap<Integer, BlockingQueue<LuaValue>> responseQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<LuaValue>> asyncResponseFutures = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(0);

    public ClientVFS(UUID fsUuid) {
        this.fsUuid = fsUuid;
    }

    public UUID getFsUuid() {
        return this.fsUuid;
    }

    public void handleResponse(int callbackId, LuaValue response) {
        if (asyncResponseFutures.containsKey(callbackId)) {
            asyncResponseFutures.remove(callbackId).complete(response);
            return;
        }
        if (responseQueues.containsKey(callbackId)) {
            // ИСПРАВЛЕНИЕ: Добавлена проверка на случай, если очередь уже удалена.
            BlockingQueue<LuaValue> queue = responseQueues.get(callbackId);
            if (queue != null) {
                queue.offer(response);
            }
        }
    }

    @Override
    public void sendRequest(int callbackId, UUID fsUuid, VfsRequestC2SPacket.Operation op, String path, String content) {
        ClientPlayNetworking.send(new VfsRequestC2SPacket(callbackId, fsUuid, op, path, content));
    }

    private int getNextCallbackId() {
        return nextCallbackId.getAndIncrement();
    }

    // --- Секция асинхронных методов (без изменений) ---
    public CompletableFuture<LuaValue> existsAsync(String path) {
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.EXISTS, path, "");
    }
    // ... и остальные асинхронные методы ...
    public CompletableFuture<LuaValue> isDirectoryAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.ISDIR, path, ""); }
    public CompletableFuture<LuaValue> readAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.READ, path, ""); }
    public CompletableFuture<LuaValue> writeAsync(String path, String content) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.WRITE, path, content); }
    public CompletableFuture<LuaValue> makeDirAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.MAKEDIR, path, ""); }
    public CompletableFuture<LuaValue> deleteAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.DELETE, path, ""); }
    public CompletableFuture<LuaValue> listAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.LIST, path, ""); }
    private CompletableFuture<LuaValue> sendAsyncRequest(VfsRequestC2SPacket.Operation op, String path, String content) {
        int callbackId = getNextCallbackId();
        CompletableFuture<LuaValue> future = new CompletableFuture<>();
        asyncResponseFutures.put(callbackId, future);
        sendRequest(callbackId, this.fsUuid, op, path, content);
        return future;
    }

    // --- Секция блокирующих методов, реализующая интерфейс IBlockingVFS ---

    private LuaValue requestBlocking(VfsRequestC2SPacket.Operation op, String path, String... data) {
        int callbackId = getNextCallbackId();
        responseQueues.put(callbackId, new LinkedBlockingQueue<>(1));
        String content = (data.length > 0) ? data[0] : "";
        sendRequest(callbackId, this.fsUuid, op, path, content);
        try {
            BlockingQueue<LuaValue> queue = responseQueues.get(callbackId);
            if (queue != null) {
                LuaValue response = queue.poll(10, TimeUnit.SECONDS);
                return response != null ? response : LuaValue.NIL;
            }
            return LuaValue.NIL;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LuaValue.NIL;
        } finally {
            responseQueues.remove(callbackId);
        }
    }

    // @Override отмечает, что мы реализуем методы из интерфейса IBlockingVFS
    @Override public LuaValue readBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.READ, path); }
    @Override public LuaValue existsBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.EXISTS, path); }
    @Override public LuaValue writeBlocking(String path, String content) { return requestBlocking(VfsRequestC2SPacket.Operation.WRITE, path, content); }
    @Override public LuaValue makeDirBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.MAKEDIR, path); }
    @Override public LuaValue isDirBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.ISDIR, path); }
    @Override public LuaValue deleteBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.DELETE, path); }
    @Override public LuaValue listBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.LIST, path); }
}