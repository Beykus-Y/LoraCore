// Полный и завершенный файл: src/client/java/com/loracore/computer/ClientVFS.java
package com.loracore.computer;

import com.loracore.network.vfs.VfsRequestC2SPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.loracore.LoraCoreMod;

public class ClientVFS implements IVfsRequester, IBlockingVFS, IAsyncVFS {
    private static final Map<UUID, ClientVFS> INSTANCES = new ConcurrentHashMap<>();

    private final UUID fsUuid;
    private final ConcurrentHashMap<Integer, BlockingQueue<String>> responseQueues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> asyncResponseFutures = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, java.util.TreeMap<Integer, String>> largeFileChunks = new ConcurrentHashMap<>();

    private ClientVFS(UUID fsUuid) {
        this.fsUuid = fsUuid;
    }

    public static synchronized ClientVFS getInstance(UUID fsUuid) {
        return INSTANCES.computeIfAbsent(fsUuid, ClientVFS::new);
    }

    public static void dispatchResponse(VfsResponseS2CPacket payload) {
        try {
            ClientVFS vfs = INSTANCES.get(payload.fsUuid());
            if (vfs == null) return;
            if (payload.type() == VfsResponseS2CPacket.ResponseType.LARGE_DATA) {
                vfs.handleLargeFileResponse(payload.callbackId(), payload.data(), payload.chunkIndex(), payload.totalChunks());
                return;
            }
            String responseValue = switch (payload.type()) {
                case TRUE -> "true";
                case FALSE -> "false";
                case STRING, TABLE_JSON -> payload.data();
                case NIL -> "";
                case LARGE_DATA -> payload.data();
            };
            vfs.handleResponse(payload.callbackId(), responseValue);
        } catch (Exception e) {
            com.loracore.LoraCoreClient.LOGGER.error("Failed to dispatch VFS response for fs_uuid {}", payload.fsUuid(), e);
        }
    }

    private void handleLargeFileResponse(int callbackId, String chunkData, int chunkIndex, int totalChunks) {
        LoraCoreMod.LOGGER.info("[ClientVFS] Received chunk {}/{} for callbackId: {}", chunkIndex + 1, totalChunks, callbackId);
        java.util.TreeMap<Integer, String> chunks = largeFileChunks.computeIfAbsent(callbackId, k -> new java.util.TreeMap<>());
        chunks.put(chunkIndex, chunkData);
        if (chunks.size() == totalChunks) {
            StringBuilder completeDataBuilder = new StringBuilder();
            for (int i = 0; i < totalChunks; i++) {
                String chunk = chunks.get(i);
                if (chunk == null) {
                    LoraCoreMod.LOGGER.error("[ClientVFS] Missing chunk {} for callbackId: {} (have {}/{})", i, callbackId, chunks.size(), totalChunks);
                    return;
                }
                completeDataBuilder.append(chunk);
            }
            String completeData = completeDataBuilder.toString();
            largeFileChunks.remove(callbackId);
            LoraCoreMod.LOGGER.info("[ClientVFS] Completed large file for callbackId: {} ({} chars, {} chunks)", callbackId, completeData.length(), totalChunks);
            handleResponse(callbackId, completeData);
        } else {
            LoraCoreMod.LOGGER.info("[ClientVFS] Waiting for more chunks for callbackId: {} (have {}/{})", callbackId, chunks.size(), totalChunks);
        }
    }

    public UUID getFsUuid() {
        return this.fsUuid;
    }

    public void handleResponse(int callbackId, String response) {
        if (asyncResponseFutures.containsKey(callbackId)) {
            asyncResponseFutures.remove(callbackId).complete(response);
            return;
        }
        if (responseQueues.containsKey(callbackId)) {
            BlockingQueue<String> queue = responseQueues.get(callbackId);
            if (queue != null) queue.offer(response);
        }
    }

    @Override
    public void sendRequest(int callbackId, UUID fsUuid, VfsRequestC2SPacket.Operation op, String path, String content) {
        ClientPlayNetworking.send(new VfsRequestC2SPacket(callbackId, fsUuid, op, path, content));
    }

    private int getNextCallbackId() {
        return nextCallbackId.getAndIncrement();
    }

    public CompletableFuture<Boolean> existsAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.EXISTS, path, "").thenApply("true"::equals);
    }
    public CompletableFuture<Boolean> isDirectoryAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.ISDIR, path, "").thenApply("true"::equals);
    }
    public CompletableFuture<String> readAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.READ, path, "");
    }
    public CompletableFuture<Boolean> writeAsync(String path, String content) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.WRITE, path, content).thenApply("true"::equals);
    }
    public CompletableFuture<Boolean> makeDirAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.MAKEDIR, path, "").thenApply("true"::equals);
    }
    public CompletableFuture<Boolean> deleteAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.DELETE, path, "").thenApply("true"::equals);
    }
    public CompletableFuture<java.util.List<String>> listAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.LIST, path, "")
            .thenApply(json -> {
                try {
                    com.google.gson.Gson G = new com.google.gson.Gson();
                    String[] arr = G.fromJson(json, String[].class);
                    if (arr == null) return java.util.List.of();
                    return java.util.List.of(arr);
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("Failed to parse listAsync JSON for path {}", path, e);
                    return java.util.List.of();
                }
            });
    }
    public CompletableFuture<String> readBytesAsync(String path) { 
        return sendAsyncRequest(VfsRequestC2SPacket.Operation.READ_BYTES, path, "");
    }

    private CompletableFuture<String> sendAsyncRequest(VfsRequestC2SPacket.Operation op, String path, String content) {
        int callbackId = getNextCallbackId();
        CompletableFuture<String> future = new CompletableFuture<>();
        asyncResponseFutures.put(callbackId, future);
        sendRequest(callbackId, this.fsUuid, op, path, content);
        return future;
    }

    private String requestBlocking(VfsRequestC2SPacket.Operation op, String path, String... data) {
        int callbackId = getNextCallbackId();
        responseQueues.put(callbackId, new LinkedBlockingQueue<>(1));
        String content = (data.length > 0) ? data[0] : "";
        sendRequest(callbackId, this.fsUuid, op, path, content);
        try {
            BlockingQueue<String> queue = responseQueues.get(callbackId);
            if (queue != null) {
                String response = queue.poll(30, TimeUnit.SECONDS);
                return response != null ? response : "";
            }
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            responseQueues.remove(callbackId);
        }
    }

    @Override public String readBlocking(String path) { return requestBlocking(VfsRequestC2SPacket.Operation.READ, path); }
    @Override public boolean existsBlocking(String path) { return "true".equals(requestBlocking(VfsRequestC2SPacket.Operation.EXISTS, path)); }
    @Override public boolean writeBlocking(String path, String content) { return "true".equals(requestBlocking(VfsRequestC2SPacket.Operation.WRITE, path, content)); }
    @Override public boolean makeDirBlocking(String path) { return "true".equals(requestBlocking(VfsRequestC2SPacket.Operation.MAKEDIR, path)); }
    @Override public boolean isDirBlocking(String path) { return "true".equals(requestBlocking(VfsRequestC2SPacket.Operation.ISDIR, path)); }
    @Override public boolean deleteBlocking(String path) { return "true".equals(requestBlocking(VfsRequestC2SPacket.Operation.DELETE, path)); }
    @Override public java.util.List<String> listBlocking(String path) { 
        String json = requestBlocking(VfsRequestC2SPacket.Operation.LIST, path);
        try {
            com.google.gson.Gson G = new com.google.gson.Gson();
            String[] arr = G.fromJson(json, String[].class);
            if (arr == null) return java.util.List.of();
            return java.util.List.of(arr);
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Failed to parse listBlocking JSON for path {}", path, e);
            return java.util.List.of();
        }
    }
}
