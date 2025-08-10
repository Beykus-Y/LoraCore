// Файл: kernel/src/main/java/com/lora/tabletos/ui/widgets/ButtonWidget.java
package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

/**
 * Интерактивный виджет-кнопка с текстом.
 */
public class ButtonWidget implements IWidget {

    protected int x, y, width, height;
    private String text;
    private final Runnable onClick; // Действие, которое выполнится при клике

    // Цвета для разных состояний
    private int colorDefault = 0xFF808080; // Серый
    private int colorHovered = 0xFFA0A0A0; // Светло-серый
    private int colorPressed = 0xFF3366CC; // Синий из MineOS
    private int textColor = 0xFFFFFFFF; // Белый

    private boolean isPressed = false;
    private boolean isHovered = false;

    public ButtonWidget(int x, int y, int width, int height, String text, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
        this.onClick = onClick;
    }

    /**
     * Проверяет, находится ли курсор мыши над кнопкой.
     */
    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width &&
                mouseY >= this.y && mouseY < this.y + this.height;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        // Обновляем состояние наведения перед каждой отрисовкой
        this.isHovered = isMouseOver(mouseX, mouseY);

        int backgroundColor;
        if (isPressed) {
            backgroundColor = colorPressed;
        } else if (isHovered) {
            backgroundColor = colorHovered;
        } else {
            backgroundColor = colorDefault;
        }

        // Рисуем фон кнопки
        g.fill(x, y, x + width, y + height, backgroundColor);
        // Рисуем текст по центру
        g.drawCenteredString(text, x + width / 2, y + (height - 8) / 2, textColor);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            if (isMouseOver(mouseEvent.mouseX, mouseEvent.mouseY)) {
                this.isPressed = true;
                if (this.onClick != null) {
                    this.onClick.run(); // Выполняем действие
                }
                return true; // "Съедаем" событие, чтобы оно не прошло дальше
            }
        }

        if (event instanceof KernelEvent.MouseReleased) {
            // Отпускаем кнопку независимо от того, где находится курсор
            this.isPressed = false;
        }

        return false;
    }

    // --- Геттеры и сеттеры ---
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; }
    @Override public void setSize(int width, int height) { this.width = width; this.height = height; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}