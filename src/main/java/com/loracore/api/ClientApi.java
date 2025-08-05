// Файл: src/main/java/com/loracore/api/ClientApi.java
package com.loracore.api;

import java.util.function.Consumer; // <-- ДОБАВЬТЕ ЭТОТ ИМПОРТ

/**
 * Этот класс служит мостом для вызова клиентского кода из общего.
 * Он использует "ленивую" регистрацию действий, которые будут заданы
 * в клиентской точке входа.
 */
public class ClientApi {

    // --- Существующий код для открытия экрана ---
    public static Runnable openTabletScreenAction = null;

    public static void openTabletScreen() {
        if (openTabletScreenAction != null) {
            openTabletScreenAction.run();
        }
    }

    // =======================================================
    //          ДОБАВЬТЕ ЭТОТ НОВЫЙ КОД НИЖЕ
    // =======================================================

    /**
     * "Действие", которое выполняет задачи в главном потоке рендеринга клиента.
     * На сервере это всегда будет null.
     */
    public static Consumer<Runnable> renderThreadExecutor = null;

    /**
     * Безопасный метод для вызова из любого потока.
     * Передает задачу на выполнение в главный поток клиента.
     * На сервере ничего не делает.
     */
    public static void executeOnRenderThread(Runnable task) {
        if (renderThreadExecutor != null) {
            renderThreadExecutor.accept(task);
        }
    }
}