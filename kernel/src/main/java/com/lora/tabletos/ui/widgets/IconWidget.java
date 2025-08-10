// Файл: kernel/src/main/java/com/lora/tabletos/ui/widgets/IconWidget.java
package com.lora.tabletos.ui.widgets;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.util.TextUtils; // Предполагаем, что этот утилитный класс будет создан

import java.util.List;

public class IconWidget implements IWidget {

    protected int x, y, width = 60, height = 50; // Фиксированный размер для иконки
    private String label;
    private final Runnable onDoubleClick;

    // В будущем здесь будет ссылка на настоящую картинку-иконку
    // private IKernelIcon iconImage; 

    // Временные цвета для иконок
    private final int ICON_BG_COLOR_FILE = 0xFFCCCCCC; // Светло-серый
    private final int ICON_BG_COLOR_DIR = 0xFFF2B233;  // Оранжевый
    private final int TEXT_COLOR = 0xFFFFFFFF;

    private final boolean isDirectory;
    private long lastClickTime = 0;

    public IconWidget(String label, boolean isDirectory, Runnable onDoubleClick) {
        this.label = label;
        this.isDirectory = isDirectory;
        this.onDoubleClick = onDoubleClick;
    }

    @Override
    public void render(IKernelGraphics g, int mouseX, int mouseY) {
        // 1. Рисуем фон иконки
        int iconColor = isDirectory ? ICON_BG_COLOR_DIR : ICON_BG_COLOR_FILE;
        g.fill(x, y, x + width, y + height - 12, iconColor); // Оставляем место для текста

        // 2. Используем нашу новую утилиту для обрезки текста!
        String displayedText = TextUtils.ellipsize(this.label, width, g);

        // 3. Рисуем отформатированный текст
        g.drawCenteredString(displayedText, x + width / 2, y + height - 10, TEXT_COLOR);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            if (isMouseOver(mouseEvent.mouseX, mouseEvent.mouseY)) {
                long currentTime = System.currentTimeMillis();
                // Проверяем на двойной клик (интервал < 500 мс)
                if (currentTime - lastClickTime < 500) {
                    if (onDoubleClick != null) {
                        onDoubleClick.run();
                    }
                }
                lastClickTime = currentTime;
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width &&
                mouseY >= this.y && mouseY < this.y + this.height;
    }

    // --- Геттеры и сеттеры ---
    @Override public void setPosition(int x, int y) { this.x = x; this.y = y; }
    @Override public void setSize(int width, int height) { /* Размер фиксирован */ }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
}