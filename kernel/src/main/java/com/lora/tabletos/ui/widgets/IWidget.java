// Файл: kernel/src/main/java/com/lora/tabletos/ui/widgets/IWidget.java
package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

/**
 * Базовый интерфейс для всех UI-компонентов (виджетов) в LoraCore OS.
 */
public interface IWidget {
    void setPosition(int x, int y);
    void setSize(int width, int height);

    int getX();
    int getY();
    int getWidth();
    int getHeight();

    /**
     * Отрисовывает виджет, используя предоставленный графический контекст.
     * @param g Графический API ядра.
     */
    void render(IKernelGraphics g, int mouseX, int mouseY);

    /**
     * Обрабатывает события ввода.
     * @param event Событие, которое нужно обработать.
     * @return true, если событие было "поглощено" виджетом, иначе false.
     */
    boolean onEvent(KernelEvent event);
}