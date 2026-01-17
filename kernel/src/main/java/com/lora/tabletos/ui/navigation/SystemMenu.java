package com.lora.tabletos.ui.navigation;

import com.lora.tabletos.ui.layout.VerticalLayout;
import com.lora.tabletos.ui.widgets.IWidget;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class SystemMenu implements IWidget {

    private final IKernelApi api;
    private final VerticalLayout layout;
    private int x, y, width, height;
    private boolean isVisible = false;

    // Цвета
    private static final int BG_COLOR = 0xFF252525;
    private static final int BORDER_COLOR = 0xFF444444;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOVER_COLOR = 0xFF3E90FF; // Синий при наведении

    public SystemMenu(IKernelApi api) {
        this.api = api;
        this.width = 140; // Чуть шире
        this.height = 0;

        this.layout = new VerticalLayout(0, 0, width);
        this.layout.setPadding(6);
        this.layout.setSpacing(4); // Больше воздуха

        // Кнопки с иконками (эмуляция текстом)
        layout.addWidget(new MenuButton("↺  Перезагрузка", api::reboot, 0xFF4CAF50)); // Зеленоватый акцент
        layout.addWidget(new MenuButton("⏻  Выключение", api::shutdown, 0xFFE53935));   // Красный акцент

        this.height = layout.getHeight() + 12;
    }


    public void toggle() {
        this.isVisible = !this.isVisible;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }

    public boolean isVisible() {
        return isVisible;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        if (!isVisible) return;

        // Эффект тени (простой)
        g.fill(x + 4, y + 4, x + width + 4, y + height + 4, 0x40000000);

        // Фон
        g.fill(x, y, x + width, y + height, BG_COLOR);

        // Тонкая обводка
        g.fill(x, y, x + width, y + 1, BORDER_COLOR); // Top
        g.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR); // Bottom
        g.fill(x, y, x + 1, y + height, BORDER_COLOR); // Left
        g.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR); // Right

        layout.render(g, mouseX, mouseY);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (!isVisible) return false;
        if (layout.onEvent(event)) return true;

        // Закрытие при клике вне
        if (event instanceof KernelEvent.MouseClicked) {
            isVisible = false;
            return true;
        }
        return false;
    }

    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        // ! КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ:
        // Мы должны сообщить layout'у его реальные координаты на экране,
        // иначе он будет думать, что он в (0,0) и hover будет работать неправильно.
        this.layout.setPosition(x, y);
    }

    // --- Внутренний класс для кнопок меню ---
    private class MenuButton implements IWidget {
        private int bx, by;
        private final int bWidth = width - 12; // Учитываем паддинг родителя
        private final int bHeight = 28; // Кнопки повыше
        private final String text;
        private final Runnable action;
        private final int accentColor;

        public MenuButton(String text, Runnable action, int accentColor) {
            this.text = text;
            this.action = action;
            this.accentColor = accentColor;
        }

        @Override
        public void render(IKernelGraphics g, int mouseX, int mouseY) {
            // Простая проверка попадания
            boolean hovered = mouseX >= bx && mouseX < bx + bWidth && mouseY >= by && mouseY < by + bHeight;

            if (hovered) {
                // Рисуем фон при наведении (скругленный)
                g.fill(bx, by, bx + bWidth, by + bHeight, HOVER_COLOR);
                // Тонкая полоска акцентного цвета слева
                g.fill(bx, by + 4, bx + 2, by + bHeight - 4, accentColor);
            }

            g.drawString(text, bx + 10, by + 10, TEXT_COLOR);
        }

        @Override
        public boolean onEvent(KernelEvent event) {
            if (event instanceof KernelEvent.MouseClicked mouseEvent) {
                if (mouseEvent.mouseX >= bx && mouseEvent.mouseX < bx + bWidth &&
                        mouseEvent.mouseY >= by && mouseEvent.mouseY < by + bHeight) {
                    action.run();
                    return true;
                }
            }
            return false;
        }

        @Override public void setPosition(int x, int y) { this.bx = x; this.by = y; }
        @Override public void setSize(int width, int height) {}
        @Override public int getX() { return bx; }
        @Override public int getY() { return by; }
        @Override public int getWidth() { return bWidth; }
        @Override public int getHeight() { return bHeight; }
    }

    // Заглушки интерфейса IWidget
    @Override public void setSize(int width, int height) { this.width = width; this.height = height; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}