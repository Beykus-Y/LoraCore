// Файл: kernel/src/main/java/com/lora/tabletos/ui/desktop/DesktopIcon.java
package com.lora.tabletos.ui.desktop;

import com.loracore.computer.kernel.IKernelGraphics;

/**
 * Представляет иконку приложения на рабочем столе.
 * Теперь с улучшенной, стилизованной отрисовкой.
 */
public class DesktopIcon {

    private final String label;
    private final String scriptPath;
    private int x, y;
    private final int width, height;

    // Цвета для иконки
    private static final int BG_COLOR = 0x50FFFFFF; // Полупрозрачный фон
    private static final int PAPER_COLOR = 0xFFDDDDDD; // Цвет "бумаги"
    private static final int PAPER_SHADOW_COLOR = 0xFFCCCCCC;
    private static final int TEXT_COLOR = 0xFFFFFFFF; // Цвет текста под иконкой
    private static final int LINE_COLOR = 0xFF555555; // Цвет строк на "документе"


    public String getLabel() { return label; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getScriptPath() { return scriptPath; }


    public DesktopIcon(String fileName, String directoryPath) {
        this.label = fileName.replace(".jar", "").replace(".lua", "")
                .replace("ai_assistant-", "AI ");
        this.scriptPath = directoryPath + "/" + fileName;
        this.width = 64;
        this.height = 64;
    }



    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isClicked(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }

    /**
     * Рисует стилизованную иконку в виде документа.
     */
    public void render(IKernelGraphics g) {
        // 1. Рисуем полупрозрачный фон для кликабельной области
        g.fill(x, y, x + width, y + height, BG_COLOR);

        // 2. Рисуем "бумагу" с тенью
        g.fill(x + 12, y + 8, x + width - 12, y + height - 8, PAPER_SHADOW_COLOR);
        g.fill(x + 14, y + 10, x + width - 14, y + height - 10, PAPER_COLOR);

        // 3. Рисуем декоративные линии, имитирующие текст
        g.fill(x + 20, y + 20, x + width - 20, y + 22, LINE_COLOR);
        g.fill(x + 20, y + 28, x + width - 20, y + 30, LINE_COLOR);
        g.fill(x + 20, y + 36, x + width - 30, y + 38, LINE_COLOR); // Короткая строка
        g.fill(x + 20, y + 44, x + width - 20, y + 46, LINE_COLOR);

        // 4. Рисуем текст под иконкой
        String textToRender = this.label;
        // Максимальная ширина текста = ширина иконки + небольшой отступ
        int maxWidth = this.width + 10;

        // 3. Если измеренная ширина текста больше допустимой, укорачиваем его
        if (g.getStringWidth(textToRender) > maxWidth) {
            // Добавляем многоточие в конец
            textToRender += "...";
            // В цикле убираем по одному символу перед многоточием, пока текст не влезет
            while (g.getStringWidth(textToRender) > maxWidth && textToRender.length() > 3) {
                textToRender = textToRender.substring(0, textToRender.length() - 4) + "...";
            }
        }

        // 4. Рисуем уже подготовленный (возможно, укороченный) текст
        g.drawCenteredString(textToRender, x + (width / 2), y + height + 2, TEXT_COLOR);
    }
}