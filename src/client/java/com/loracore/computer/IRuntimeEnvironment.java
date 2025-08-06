// Полный исправленный файл: src/client/java/com/loracore/computer/IRuntimeEnvironment.java
package com.loracore.computer;

import net.minecraft.client.gui.DrawContext;

/**
 * Абстракция над средой выполнения планшета (Lua VM или Java Kernel).
 * Позволяет TabletScreen управлять любой средой через единый интерфейс.
 */
public interface IRuntimeEnvironment {

    /**
     * Запускает среду выполнения.
     * @param bootPath Путь к основному файлу для загрузки (например, "/os/recovery.lua" или "/boot/kernel.jar").
     */
    void boot(String bootPath);

    /**
     * Вызывается каждый кадр для отрисовки содержимого.
     */
    void render(DrawContext context, int mouseX, int mouseY, float delta);

    /**
     * Вызывается каждый игровой тик для фоновой логики.
     */
    void tick();

    /**
     * Безопасно завершает работу среды и освобождает ресурсы.
     */
    void shutdown();

    // --- Методы обработки ввода ---
    /**
     * Проверяет, активна ли среда выполнения.
     * @return true, если среда работает и не "упала".
     */
    boolean isRunning();

    /**
     * Возвращает сообщение об ошибке, если среда "упала".
     * @return Сообщение об ошибке или null.
     */
    String getCrashMessage();

    /**
     * Вызывается при нажатии клавиши.
     * @return true, если событие было обработано.
     */
    boolean onKeyPressed(int keyCode, int scanCode, int modifiers);

    /**
     * Вызывается при отпускании клавиши.
     * @return true, если событие было обработано.
     */
    boolean onKeyReleased(int keyCode, int scanCode, int modifiers);

    /**
     * Вызывается при вводе символа.
     * @return true, если событие было обработано.
     */
    boolean onCharTyped(char chr, int modifiers);

    /**
     * Вызывается при прокрутке колеса мыши.
     * @return true, если событие было обработано.
     */
    boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount);

    /**
     * Вызывается при нажатии кнопки мыши.
     * @return true, если событие было обработано.
     */
    boolean onMouseClicked(double mouseX, double mouseY, int button);

    /**
     * Вызывается при отпускании кнопки мыши.
     * @return true, если событие было обработано.
     */
    boolean onMouseReleased(double mouseX, double mouseY, int button);
}