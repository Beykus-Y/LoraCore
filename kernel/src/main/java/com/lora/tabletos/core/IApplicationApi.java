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
     * Запускает Lua-скрипт в изолированном окружении.
     * @param path Путь к Lua-скрипту
     * @return CompletableFuture с результатом выполнения
     */
    CompletableFuture<Boolean> runLuaScript(String path);

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
}
