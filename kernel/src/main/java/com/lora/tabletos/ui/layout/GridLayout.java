// Файл: kernel/src/main/java/com/lora/tabletos/ui/layout/GridLayout.java
package com.lora.tabletos.ui.layout;

import com.lora.tabletos.ui.widgets.IWidget;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

import java.util.ArrayList;
import java.util.List;

public class GridLayout implements IWidget {

    protected int x, y, width, height;
    private final List<IWidget> children = new ArrayList<>();

    private final int columnCount;
    private int cellWidth = 60;  // Ширина ячейки по умолчанию
    private int cellHeight = 60; // Высота ячейки по умолчанию

    private int padding = 5;
    private int spacingX = 10; // Горизонтальный отступ
    private int spacingY = 10; // Вертикальный отступ

    public GridLayout(int x, int y, int width, int columnCount) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.columnCount = Math.max(1, columnCount); // Минимум 1 колонка
    }

    public void addWidget(IWidget widget) {
        this.children.add(widget);
        recalculateLayout();
    }

    private void recalculateLayout() {
        if (children.isEmpty()) return;

        int col = 0;
        int row = 0;

        for (IWidget child : children) {
            int childX = this.x + padding + (col * (cellWidth + spacingX));
            int childY = this.y + padding + (row * (cellHeight + spacingY));

            // Помещаем виджет в центр ячейки
            int centeredChildX = childX + (cellWidth - child.getWidth()) / 2;
            int centeredChildY = childY + (cellHeight - child.getHeight()) / 2;

            child.setPosition(centeredChildX, centeredChildY);

            col++;
            if (col >= columnCount) {
                col = 0;
                row++;
            }
        }

        // Автоматически пересчитываем высоту всего лэйаута, чтобы ScrollPane знал, насколько можно скроллить
        this.height = (row + 1) * (cellHeight + spacingY) + padding * 2;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        for (IWidget child : children) {
            child.render(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).onEvent(event)) {
                return true;
            }
        }
        return false;
    }

    // --- Геттеры и сеттеры ---
    public void setCellSize(int cellWidth, int cellHeight) { this.cellWidth = cellWidth; this.cellHeight = cellHeight; recalculateLayout(); }
    public void setPadding(int padding) { this.padding = padding; recalculateLayout(); }
    public void setSpacing(int spacingX, int spacingY) { this.spacingX = spacingX; this.spacingY = spacingY; recalculateLayout(); }
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; recalculateLayout(); }
    @Override public void setSize(int width, int height) { this.width = width; } // Высота рассчитывается автоматически
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}