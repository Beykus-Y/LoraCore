// Новый файл: src/client/java/com/loracore/computer/kernel/IKernelApi.java
package com.loracore.computer.kernel;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * API, предоставляемое ядру для безопасного взаимодействия с модом.
 */
public interface IKernelApi {
    /**
     * Возвращает безопасный доступ к виртуальной файловой системе планшета.
     */
    IKernelVfs getVfs();

    /**
     * Запускает Lua-скрипт в изолированном окружении.
     * @param path Путь к Lua-скрипту в VFS
     * @return CompletableFuture<Boolean> который завершается true при успешном запуске, false при ошибке
     */
    CompletableFuture<Boolean> runLuaScript(String path);

    /**
     * Устанавливает исполнитель Lua-кода для ядра
     * @param executor Consumer<String> который принимает содержимое Lua-скрипта и выполняет его
     */
    void setLuaExecutor(Consumer<String> executor);

    /**
     * Отправляет сообщение в указанный Lua-поток.
     */
    void sendToLua(int threadId, Object... message);

    /**
     * Возвращает размер терминала в символах (ширина, высота).
     * Полезно для совместимости с Lua-программами.
     */
    int[] getTerminalSize();

    /**
     * Перезагружает виртуальную машину.
     */
    void reboot();

    /**
     * Выключает виртуальную машину.
     */
    void shutdown();

    /**
     * Возвращает доступ к графическому API ядра.
     */
    IKernelGraphics getGraphics();


}