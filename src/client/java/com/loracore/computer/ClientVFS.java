// Файл: src/client/java/com/loracore/computer/ClientVFS.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.network.vfs.VfsRequestC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.luaj.vm2.LuaValue;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ИЗМЕНЕНИЕ: Реализуем наш новый интерфейс
public class ClientVFS implements IVfsRequester {
    private final UUID fsUuid;
    private final ConcurrentHashMap<Integer, BlockingQueue<LuaValue>> responseQueues = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(0);

    public ClientVFS(UUID fsUuid) { this.fsUuid = fsUuid; }

    public UUID getFsUuid() {
        return this.fsUuid;
    }

    public void handleResponse(int callbackId, LuaValue response) {
        LoraCoreMod.LOGGER.info("[ClientVFS] Received response for callbackId={}, response={}", callbackId, response);
        if (responseQueues.containsKey(callbackId)) {
            responseQueues.get(callbackId).offer(response);
        }
    }

    // ИЗМЕНЕНИЕ: Реализация метода из интерфейса IVfsRequester
    @Override
    public void sendRequest(int callbackId, UUID fsUuid, VfsRequestC2SPacket.Operation op, String path, String content) {
        ClientPlayNetworking.send(new VfsRequestC2SPacket(callbackId, fsUuid, op, path, content));
    }

    private int getNextCallbackId() {
        return nextCallbackId.getAndIncrement();
    }

    /**
     * Блокирующий метод, используемый ТОЛЬКО для начальной загрузки BIOS/Recovery.
     */
    public LuaValue readBlocking(String path) {
        int callbackId = getNextCallbackId();
        responseQueues.put(callbackId, new LinkedBlockingQueue<>(1));

        sendRequest(callbackId, this.fsUuid, VfsRequestC2SPacket.Operation.READ, path, "");

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
}