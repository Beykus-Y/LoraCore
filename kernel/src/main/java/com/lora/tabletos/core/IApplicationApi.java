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
}
