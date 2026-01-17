package com.lora.tabletos.ui.layout;

import com.lora.tabletos.ui.widgets.IWidget;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class UIContainer implements IWidget {
    protected int x, y, width, height;
    protected final List<IWidget> children = new ArrayList<>();
    protected int padding = 5;
    protected int spacing = 5;

    public void addWidget(IWidget widget) {
        children.add(widget);
        recalculateLayout();
    }

    public void clear() {
        children.clear();
        recalculateLayout();
    }

    protected abstract void recalculateLayout();

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

    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; recalculateLayout(); }
    @Override public void setSize(int width, int height) { this.width = width; this.height = height; recalculateLayout(); }

    public void setPadding(int padding) { this.padding = padding; recalculateLayout(); }
    public void setSpacing(int spacing) { this.spacing = spacing; recalculateLayout(); }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}