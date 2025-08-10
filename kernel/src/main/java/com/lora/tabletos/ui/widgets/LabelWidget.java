// Файл: kernel/src/main/java/com/lora/tabletos/ui/widgets/LabelWidget.java
package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class LabelWidget implements IWidget {

    protected int x, y, width, height;
    private String text;
    private int color;

    public LabelWidget(int x, int y, String text, int color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
        this.width = 0; // Ширина будет рассчитана при первом рендере
        this.height = 10;
    }

    public void setText(String text) {
        this.text = text;
    }

    /**
     * ИСПРАВЛЕННЫЙ МЕТОД RENDER
     * Теперь он принимает mouseX и mouseY, как того требует интерфейс IWidget.
     */
    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        // Рассчитываем ширину, если она не задана
        if (this.width == 0) {
            this.width = g.getStringWidth(this.text);
        }
        g.drawString(text, x, y, color);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
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