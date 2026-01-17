package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class ScrollPane implements IWidget {

    protected int x, y, width, height;
    private IWidget content;

    private double scrollY = 0;
    private int totalContentHeight = 0;

    public ScrollPane(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public void setContent(IWidget content) {
        this.content = content;
        if (content != null) {
            // Контент должен знать свою ширину
            content.setSize(this.width, 0); // Высоту он сам посчитает
            content.setPosition(x, y);
        }
    }

    // Метод для проверки наведения мыши
    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width &&
                mouseY >= this.y && mouseY < this.y + this.height;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        if (content == null) return;

        // 1. Всегда обновляем высоту контента перед отрисовкой!
        this.totalContentHeight = content.getHeight();

        g.enableScissor(x, y, width, height);
        g.pushMatrix();
        g.translate(0, -scrollY, 0);

        // Рендерим контент (смещаем mouseY для корректного hover внутри)
        content.render(g, mouseX, (int)(mouseY + scrollY));

        g.popMatrix();
        g.disableScissor();

        // Скроллбар
        if (totalContentHeight > height) {
            int barW = 6;
            int barX = x + width - barW;

            // Фон скролла
            g.fill(barX, y, barX + barW, y + height, 0x20FFFFFF);

            float ratio = (float) height / totalContentHeight;
            int thumbH = Math.max(30, (int)(height * ratio));
            int thumbY = y + (int)((height - thumbH) * (scrollY / (totalContentHeight - height)));

            g.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0x80FFFFFF);
        }
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (content == null) return false;

        // 1. Обработка колесика
        if (event instanceof KernelEvent.MouseScrolled scrollEvent) {
            if (isMouseOver(scrollEvent.mouseX, scrollEvent.mouseY)) {
                // Скорость скролла
                double speed = 30.0;
                scrollY -= scrollEvent.verticalAmount * speed;

                clampScroll();
                return true;
            }
        }

        // 2. Передача событий (кликов) детям
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            if (isMouseOver(mouseEvent.mouseX, mouseEvent.mouseY)) {
                KernelEvent subEvent = new KernelEvent.MouseClicked(
                        mouseEvent.mouseX,
                        mouseEvent.mouseY + scrollY, // Корректируем Y
                        mouseEvent.button
                );
                return content.onEvent(subEvent);
            }
        }

        // Клавиатуру передаем всегда
        if (event instanceof KernelEvent.KeyPressed || event instanceof KernelEvent.CharTyped) {
            return content.onEvent(event);
        }

        return false;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, totalContentHeight - height);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    // Сеттеры
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; if(content!=null) content.setPosition(x,y); }
    @Override public void setSize(int w, int h) { this.width = w; this.height = h; if(content!=null) content.setSize(w,0); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}