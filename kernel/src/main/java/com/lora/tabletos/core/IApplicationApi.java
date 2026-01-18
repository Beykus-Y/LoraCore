package com.lora.tabletos.core;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.IKernelVfs;

import java.util.concurrent.CompletableFuture;

/**
 * Безопасный API для приложений, предоставляющий только необходимые функции.
 * Это урезанная версия IKernelApi, которая не дает приложениям полный доступ к системе.
 */
public interface IApplicationApi {
    
    /**
     * Возвращает доступ к виртуальной файловой системе.
     * @return IKernelVfs для работы с файлами
     */
    IKernelVfs getVfs();
    
    /**
     * Возвращает доступ к графическому API.
     * @return IKernelGraphics для рисования
     */
    IKernelGraphics getGraphics();
    
    /**
     * Отправляет запрос к ИИ и возвращает ответ.
     * @param prompt Запрос к ИИ
     * @return CompletableFuture с ответом от ИИ
     */
    CompletableFuture<String> askAI(String prompt);
    
    /**
     * Возвращает размер экрана.
     * @return массив [ширина, высота]
     */
    int[] getScreenSize();
    
    

    /**
     * Показывает всплывающее уведомление.
     * @param message Текст уведомления.
     * @param isError Если true - уведомление красное, иначе синее/зеленое.
     */
    void showNotification(String message, boolean isError);

    /**
     * [НОВЫЙ МЕТОД]
     * Безопасно выполняет задачу в главном потоке отрисовки клиента.
     * @param task Задача для выполнения.
     */
    void runOnRenderThread(Runnable task);

    /**
     * Асинхронно вызывает метод на серверном устройстве.
     * @param deviceType Тип устройства (например, "redstone").
     * @param methodName Имя метода для вызова (например, "getPower").
     * @param args Аргументы для метода.
     * @return Future, который завершится с результатом от устройства.
     */
    CompletableFuture<Object[]> invokeDevice(String deviceType, String methodName, Object... args);
    
    /**
     * Возвращает текущие метрики системы.
     * @return Map с метриками: "cpu_load" (0.0-1.0), "ram_used_kb", "ram_total_kb", "disk_queue" (размер очереди задач)
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
