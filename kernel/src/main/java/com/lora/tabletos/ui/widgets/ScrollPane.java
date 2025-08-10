// Файл: kernel/src/main/java/com/lora/tabletos/ui/widgets/ScrollPane.java
package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class ScrollPane implements IWidget {

    protected int x, y, width, height;
    private IWidget content;

    private double scrollY = 0;
    private int totalContentHeight = 0;

    // Цвета для полосы прокрутки
    private final int SCROLLBAR_BG_COLOR = 0xFF333333;
    private final int SCROLLBAR_THUMB_COLOR = 0xFF888888;

    public ScrollPane(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Устанавливает виджет, который будет прокручиваться внутри этой панели.
     */
    public void setContent(IWidget content) {
        this.content = content;
        // Помещаем контент в левый верхний угол панели
        this.content.setPosition(this.x, this.y);
        this.totalContentHeight = content.getHeight();
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width &&
                mouseY >= this.y && mouseY < this.y + this.height;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        if (content == null) return;

        // 1. Включаем отсечение. Все, что рисуется дальше, будет видно только в пределах этой панели.
        g.enableScissor(x, y, width, height);

        // 2. Смещаем систему координат вверх на величину скролла.
        g.pushMatrix();
        g.translate(0, -scrollY, 0);

        // 3. Рендерим контент. Он будет нарисован со смещением, но "увидим" мы только его часть благодаря scissor.
        content.render(g, mouseX, (int) (mouseY + scrollY));

        // 4. Возвращаем систему координат в исходное состояние.
        g.popMatrix();

        // 5. Отключаем отсечение, чтобы не влиять на рендеринг других элементов.
        g.disableScissor();

        // 6. Рисуем полосу прокрутки, если контент больше видимой области.
        if (totalContentHeight > height) {
            renderScrollbar(g);
        }
    }

    private void renderScrollbar(IKernelGraphics g) {
        int scrollbarWidth = 8;
        int scrollbarX = x + width - scrollbarWidth;

        // Фон полосы прокрутки
        g.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, SCROLLBAR_BG_COLOR);

        // Бегунок
        float viewableRatio = (float) height / totalContentHeight;
        int thumbHeight = (int) (height * viewableRatio);
        thumbHeight = Math.max(10, thumbHeight); // Минимальная высота бегунка

        float scrollRatio = (float) scrollY / (totalContentHeight - height);
        int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

        g.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (content == null) return false;

        // События мыши должны быть переданы дочернему элементу с учетом скролла
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            if (isMouseOver(mouseEvent.mouseX, mouseEvent.mouseY)) {
                // Трансформируем координаты для дочернего элемента
                KernelEvent transformedEvent = new KernelEvent.MouseClicked(
                        mouseEvent.mouseX,
                        mouseEvent.mouseY + scrollY,
                        mouseEvent.button
                );
                return content.onEvent(transformedEvent);
            }
        }

        if (event instanceof KernelEvent.MouseScrolled scrollEvent) {
            if (isMouseOver(scrollEvent.mouseX, scrollEvent.mouseY)) {
                // Обновляем позицию скролла
                this.scrollY -= scrollEvent.verticalAmount * 10; // Умножитель для скорости

                // Ограничиваем скролл, чтобы не уехать за пределы контента
                int maxScroll = totalContentHeight - height;
                if (maxScroll < 0) maxScroll = 0;

                if (this.scrollY < 0) this.scrollY = 0;
                if (this.scrollY > maxScroll) this.scrollY = maxScroll;

                return true; // Событие обработано
            }
        }

        // Другие события можно передавать как есть
        return content.onEvent(event);
    }

    // --- Геттеры и сеттеры ---
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; recalculateLayout(); }
    @Override public void setSize(int width, int height) { this.width = width; this.height = height; recalculateLayout(); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    private void recalculateLayout() { if (content != null) content.setPosition(x, y); }
}