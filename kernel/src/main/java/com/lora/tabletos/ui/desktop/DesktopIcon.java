package com.lora.tabletos.ui.desktop;

import com.loracore.computer.kernel.IKernelGraphics;

/**
 * Представляет иконку приложения на рабочем столе.
 */
public class DesktopIcon {
    
    private final String label;
    private final String scriptPath;
    private int x, y;
    private final int width, height;
    
    private static final int ICON_BG_COLOR = 0x50FFFFFF;
    private static final int ICON_PAPER_COLOR = 0xFFDDDDDD;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    
    public DesktopIcon(String fileName, String directoryPath) {
        // Убираем расширение файла для отображения имени
        this.label = fileName.replace(".lua", "").replace(".jar", "");
        this.scriptPath = directoryPath + "/" + fileName;
        this.width = 64;
        this.height = 64;
    }
    
    // --- Геттеры ---
    public String getLabel() { return label; }
    public String getScriptPath() { return scriptPath; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    /**
     * Устанавливает позицию иконки.
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    /**
     * Проверяет, был ли клик по иконке.
     */
    public boolean isClicked(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
    }
    
    /**
     * Рисует иконку.
     */
    public void render(IKernelGraphics g) {
        // Рисуем фон иконки
        g.fill(x, y, x + width, y + height, ICON_BG_COLOR);
        
        // Рисуем "бумагу" внутри иконки
        g.fill(x + 12, y + 8, x + width - 12, y + height - 8, ICON_PAPER_COLOR);
        g.fill(x + 14, y + 10, x + width - 14, y + height - 10, 0xFFFFFFFF);
        
        // Рисуем текст под иконкой (отцентрированный)
        int textWidth = g.getStringWidth(label);
        g.drawString(label, x + (width - textWidth) / 2, y + height + 2, TEXT_COLOR);
    }
}
