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

    /**
     * [НОВЫЙ МЕТОД]
     * Асинхронно вызывает метод на серверном устройстве.
     * @param deviceType Тип устройства (например, "redstone").
     * @param methodName Имя метода для вызова (например, "getPower").
     * @param args Аргументы для метода.
     * @return Future, который завершится с результатом от устройства.
     */
    CompletableFuture<Object[]> invokeDevice(String deviceType, String methodName, Object... args);

    /**
     * Возвращает текущие метрики системы.
     * @return Map с метриками: "cpu_load" (0.0-1.0), "ram_used_kb", "ram_total_kb", "disk_queue"
     */
    java.util.Map<String, Double> getSystemMetrics();
    
    /**
     * Возвращает строковое представление UUID планшета.
     * @return UUID планшета в виде строки, или "N/A" если недоступно
     */
    String getTabletUuidStr();
    
    /**
     * Возвращает строковое представление UUID файловой системы.
     * @return UUID файловой системы в виде строки, или "N/A" если недоступно
     */
    String getFsUuidStr();
}
