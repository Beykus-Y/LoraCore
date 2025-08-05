// Файл: src/main/java/com/loracore/computer/IVfsRequester.java
package com.loracore.computer;

import com.loracore.network.vfs.VfsRequestC2SPacket.Operation;
import java.util.UUID;

/**
 * Интерфейс-мост для отправки VFS запросов из общего кода (VM)
 * на клиенте, не зная о деталях реализации сети.
 */
public interface IVfsRequester {
    /**
     * Отправляет асинхронный запрос на сервер.
     * @param callbackId Уникальный ID для этого запроса.
     * @param fsUuid UUID файловой системы.
     * @param op Операция (READ, WRITE и т.д.).
     * @param path Путь к файлу/папке.
     * @param content Содержимое для записи (используется в WRITE).
     */
    void sendRequest(int callbackId, UUID fsUuid, Operation op, String path, String content);
}