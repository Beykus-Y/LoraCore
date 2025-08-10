// Файл: kernel/src/main/java/com/lora/tabletos/ui/layout/LinearLayout.java
package com.lora.tabletos.ui.layout;

import com.lora.tabletos.ui.widgets.IWidget;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

import java.util.ArrayList;
import java.util.List;

public class LinearLayout implements IWidget {

    protected int x, y, width, height;
    private final List<IWidget> children = new ArrayList<>();
    private final Orientation orientation;

    private int padding = 5; // Отступ внутри контейнера
    private int spacing = 5; // Пространство между элементами

    public LinearLayout(int x, int y, int width, int height, Orientation orientation) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
    }

    public void addWidget(IWidget widget) {
        this.children.add(widget);
        recalculateLayout(); // Пересчитываем позиции при добавлении
    }

    private void recalculateLayout() {
        int currentX = this.x + padding;
        int currentY = this.y + padding;

        for (IWidget child : children) {
            child.setPosition(currentX, currentY);

            if (orientation == Orientation.VERTICAL) {
                currentY += child.getHeight() + spacing;
            } else {
                currentX += child.getWidth() + spacing;
            }
        }
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        // Опционально: можно отрисовать фон самого лэйаута для отладки
        // g.fill(x, y, x + width, y + height, 0x50FF0000); // Полупрозрачный красный

        // Рендерим всех детей
        for (IWidget child : children) {
            child.render(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        // Передаем событие дочерним элементам в обратном порядке,
        // чтобы верхние элементы получали его первыми.
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onEvent(event)) {
                return true; // Если ребенок обработал событие, прекращаем передачу
            }
        }
        return false;
    }

    // --- Геттеры и сеттеры ---
    public void setPadding(int padding) { this.padding = padding; recalculateLayout(); }
    public void setSpacing(int spacing) { this.spacing = spacing; recalculateLayout(); }
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; recalculateLayout(); }
    @Override public void setSize(int width, int height) { this.width = width; this.height = height; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}