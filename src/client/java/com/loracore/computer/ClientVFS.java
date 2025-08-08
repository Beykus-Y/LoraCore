// Полный и завершенный файл: src/client/java/com/loracore/computer/ClientVFS.java
package com.loracore.computer;

import com.loracore.network.vfs.VfsRequestC2SPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.loracore.LoraCoreMod;

// ИСПРАВЛЕНИЕ: Класс теперь реализует и старый IVfsRequester, и новый IBlockingVFS, и IAsyncVFS.
// УДАЛЕН Closeable - ClientVFS теперь управляется как синглтон.
public class ClientVFS implements IVfsRequester, IBlockingVFS, IAsyncVFS {
    private static final Map<UUID, ClientVFS> INSTANCES = new ConcurrentHashMap<>();

    private final UUID fsUuid;
    private final ConcurrentHashMap<Integer, BlockingQueue<LuaValue>> responseQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<LuaValue>> asyncResponseFutures = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(0);
    
    // ИСПРАВЛЕНО: Добавляем поддержку больших файлов
    private final ConcurrentHashMap<Integer, StringBuilder> largeFileBuffers = new ConcurrentHashMap<>();

    // Сделаем конструктор приватным - теперь используется только через getInstance
    private ClientVFS(UUID fsUuid) {
        this.fsUuid = fsUuid;
    }

    // Статический метод для получения экземпляра синглтона
    public static synchronized ClientVFS getInstance(UUID fsUuid) {
        return INSTANCES.computeIfAbsent(fsUuid, ClientVFS::new);
    }

    public static void dispatchResponse(VfsResponseS2CPacket payload) {
        try {
            ClientVFS vfs = INSTANCES.get(payload.fsUuid());
            if (vfs == null) {
                return;
            }
            
            // ИСПРАВЛЕНО: Обработка больших файлов
            if (payload.type() == VfsResponseS2CPacket.ResponseType.LARGE_DATA) {
                vfs.handleLargeFileResponse(payload.callbackId(), payload.data(), payload.chunkIndex(), payload.totalChunks());
                return;
            }
            
            LuaValue responseValue = switch (payload.type()) {
                case TRUE -> LuaValue.TRUE;
                case FALSE -> LuaValue.FALSE;
                case STRING, TABLE_JSON -> LuaValue.valueOf(payload.data());
                default -> LuaValue.NIL;
            };
            vfs.handleResponse(payload.callbackId(), responseValue);
        } catch (Exception e) {
            com.loracore.LoraCoreClient.LOGGER.error("Failed to dispatch VFS response for fs_uuid {}", payload.fsUuid(), e);
        }
    }
    
    /**
     * Обрабатывает ответы для больших файлов, собирая их по частям
     */
    private void handleLargeFileResponse(int callbackId, String chunkData, int chunkIndex, int totalChunks) {
        LoraCoreMod.LOGGER.info("[ClientVFS] Received chunk {}/{} for callbackId: {}", chunkIndex + 1, totalChunks, callbackId);
        
        StringBuilder buffer = largeFileBuffers.computeIfAbsent(callbackId, k -> new StringBuilder());
        buffer.append(chunkData);
        
        // Если это последний чанк, собираем полный файл
        if (chunkIndex >= totalChunks - 1) {
            String completeData = buffer.toString();
            largeFileBuffers.remove(callbackId);
            
            LoraCoreMod.LOGGER.info("[ClientVFS] Completed large file for callbackId: {} ({} chars)", callbackId, completeData.length());
            
            // Отправляем полный файл как обычный ответ
            handleResponse(callbackId, LuaValue.valueOf(completeData));
        } else {
            LoraCoreMod.LOGGER.info("[ClientVFS] Waiting for more chunks for callbackId: {}", callbackId);
        }
        // Иначе ждем следующий чанк
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

    // --- Секция асинхронных методов ---
    public CompletableFuture<LuaValue> existsAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.EXISTS, path, ""); }
    public CompletableFuture<LuaValue> isDirectoryAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.ISDIR, path, ""); }
    public CompletableFuture<LuaValue> readAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.READ, path, ""); }
    public CompletableFuture<LuaValue> writeAsync(String path, String content) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.WRITE, path, content); }
    public CompletableFuture<LuaValue> makeDirAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.MAKEDIR, path, ""); }
    public CompletableFuture<LuaValue> deleteAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.DELETE, path, ""); }
    public CompletableFuture<LuaValue> listAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.LIST, path, ""); }
    public CompletableFuture<LuaValue> readBytesAsync(String path) { return sendAsyncRequest(VfsRequestC2SPacket.Operation.READ_BYTES, path, ""); }

    private CompletableFuture<LuaValue> sendAsyncRequest(VfsRequestC2SPacket.Operation op, String path, String content) {
        int callbackId = getNextCallbackId();
        CompletableFuture<LuaValue> future = new CompletableFuture<>();
        asyncResponseFutures.put(callbackId, future);
        sendRequest(callbackId, this.fsUuid, op, path, content);
        return future;
    }

    // --- Блокирующие методы IBlockingVFS ---
    private LuaValue requestBlocking(VfsRequestC2SPacket.Operation op, String path, String... data) {
        int callbackId = getNextCallbackId();
        responseQueues.put(callbackId, new LinkedBlockingQueue<>(1));
        String content = (data.length > 0) ? data[0] : "";
        sendRequest(callbackId, this.fsUuid, op, path, content);
        try {
            BlockingQueue<LuaValue> queue = responseQueues.get(callbackId);
            if (queue != null) {
                LuaValue response = queue.poll(30, TimeUnit.SECONDS); // ИСПРАВЛЕНО: Увеличиваем таймаут для больших файлов
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

    @Override public LuaValue readBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.READ, path); }
    @Override public LuaValue existsBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.EXISTS, path); }
    @Override public LuaValue writeBlocking(String path, String content) { return requestBlocking(VfsRequestC2SPacket.Operation.WRITE, path, content); }
    @Override public LuaValue makeDirBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.MAKEDIR, path); }
    @Override public LuaValue isDirBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.ISDIR, path); }
    @Override public LuaValue deleteBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.DELETE, path); }
    @Override public LuaValue listBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.LIST, path); }
}