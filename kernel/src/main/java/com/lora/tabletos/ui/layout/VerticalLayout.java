package com.lora.tabletos.ui.layout;

import com.lora.tabletos.ui.widgets.IWidget;

public class VerticalLayout extends UIContainer {

    public VerticalLayout(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = 0;
    }

    @Override
    protected void recalculateLayout() {
        int currentY = this.y + padding;
        int currentX = this.x + padding;

        for (IWidget child : children) {
            child.setPosition(currentX, currentY);
            currentY += child.getHeight() + spacing;
        }

        this.height = currentY - this.y + padding - spacing;
    }
}