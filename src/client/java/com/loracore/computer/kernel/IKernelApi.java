// Новый файл: src/client/java/com/loracore/computer/kernel/IKernelApi.java
package com.loracore.computer.kernel;

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
     * @return ID потока для дальнейшего взаимодействия.
     */
    int runLuaScript(String path);

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
}