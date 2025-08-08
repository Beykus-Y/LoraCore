// Новый файл: src/main/java/com/loracore/api/GpuApi.java
package com.loracore.api;

import com.loracore.network.graphics.GpuCommand;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * API-мост для отправки GPU команд из общего кода, не имея прямого доступа
 * к клиентской сетевой части. Реализация будет предоставлена в LoraCoreClient.
 */
public class GpuApi {

    /**
     * Действие, которое нужно реализовать на клиенте.
     * Принимает UUID планшета и саму команду.
     */
    public static BiConsumer<UUID, GpuCommand> sendCommandAction = null;

    /**
     * Безопасный метод для вызова из общего кода.
     */
    public static void sendCommand(UUID tabletUuid, GpuCommand command) {
        if (sendCommandAction != null) {
            sendCommandAction.accept(tabletUuid, command);
        }
    }
}