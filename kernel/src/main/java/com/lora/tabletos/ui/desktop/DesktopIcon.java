package com.lora.tabletos.ui.desktop;

import com.loracore.computer.kernel.IKernelGraphics;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.util.TextUtils;

public class DesktopIcon {

    private final String label;
    private final String scriptPath;
    private final Class<? extends IApplication> appClass; // Для встроенных приложений
    private final boolean isBuiltIn;
    private int x, y;

    // Увеличили размер иконки под новое разрешение
    private final int width = 80;
    private final int height = 90;

    // Цвета
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int FILE_COLOR = 0xFFE0E0E0;     // Белый лист
    private static final int FILE_ACCENT = 0xFF4CAF50;    // Зеленая шапка
    private static final int FOLDER_COLOR = 0xFFFBC02D;   // Желтая папка
    private static final int FOLDER_SHADOW = 0xFFF57F17;  // Темная часть папки
    
    // Цвета для встроенных приложений
    private static final int BUILTIN_SYSTEM_MONITOR = 0xFF2196F3;  // Синий
    private static final int BUILTIN_ABOUT = 0xFF4CAF50;           // Зеленый
    private static final int BUILTIN_SETTINGS = 0xFFFF9800;        // Оранжевый

    private final boolean isFolder; // (Пока все скрипты считаем файлами, но для будущего)

    /**
     * Конструктор для внешних приложений (из файлов).
     */
    public DesktopIcon(String fileName, String directoryPath) {
        // Убираем расширения для красоты
        this.label = fileName.replace(".jar", "").replace(".lua", "");
        this.scriptPath = directoryPath + "/" + fileName;
        this.appClass = null;
        this.isBuiltIn = false;
        this.isFolder = false; // В будущем можно проверять isDirectory
    }

    /**
     * Конструктор для встроенных приложений.
     */
    public DesktopIcon(Class<? extends IApplication> appClass, String name, String iconLabel) {
        this.label = iconLabel;
        this.scriptPath = null;
        this.appClass = appClass;
        this.isBuiltIn = true;
        this.isFolder = false;
    }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getLabel() { return label; }
    public String getScriptPath() { return scriptPath; }
    public Class<? extends IApplication> getAppClass() { return appClass; }
    public boolean isBuiltIn() { return isBuiltIn; }

    public boolean isClicked(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public void render(IKernelGraphics g) {
        int iconSize = 50;
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + 5;

        if (isBuiltIn) {
            // Встроенные приложения имеют особые иконки
            if (appClass != null) {
                String className = appClass.getSimpleName();
                if (className.equals("SystemMonitorApp")) {
                    drawBuiltInIcon(g, iconX, iconY, iconSize, BUILTIN_SYSTEM_MONITOR, "📊");
                } else if (className.equals("AboutApp")) {
                    drawBuiltInIcon(g, iconX, iconY, iconSize, BUILTIN_ABOUT, "ℹ");
                } else if (className.equals("SettingsApp")) {
                    drawBuiltInIcon(g, iconX, iconY, iconSize, BUILTIN_SETTINGS, "⚙");
                } else {
                    drawBuiltInIcon(g, iconX, iconY, iconSize, 0xFF9E9E9E, "?");
                }
            }
        } else if (label.toLowerCase().contains("terminal")) {
            drawTerminalIcon(g, iconX, iconY, iconSize);
        } else if (label.toLowerCase().contains("ai")) {
            drawAiIcon(g, iconX, iconY, iconSize);
        } else {
            drawFileIcon(g, iconX, iconY, iconSize);
        }

        // Текст под иконкой
        String displayLabel = TextUtils.ellipsize(label, width, g);
        g.drawCenteredString(displayLabel, x + width/2, y + iconSize + 15, TEXT_COLOR);
    }

    // Рисуем файл (лист бумаги с загнутым уголком)
    private void drawFileIcon(IKernelGraphics g, int x, int y, int s) {
        g.fill(x, y, x + s, y + s, FILE_COLOR);
        // Имитация текста
        int lineH = 2;
        int lineGap = 6;
        for(int i=10; i<s-10; i+=lineGap) {
            g.fill(x + 10, y + i, x + s - 10, y + i + lineH, 0xFFAAAAAA);
        }
    }

    // Рисуем Терминал (черный экран с >_)
    private void drawTerminalIcon(IKernelGraphics g, int x, int y, int s) {
        g.fill(x, y, x + s, y + s, 0xFF212121); // Черный фон
        g.fill(x + 2, y + 2, x + s - 2, y + 12, 0xFF424242); // Заголовок

        g.drawString(">_", x + 8, y + 20, 0xFF4CAF50, 1.5f);
    }

    // Рисуем AI (Мозг/Чип)
    private void drawAiIcon(IKernelGraphics g, int x, int y, int s) {
        g.fill(x, y, x + s, y + s, 0xFF673AB7); // Фиолетовый
        // "Глаз" по центру
        g.fill(x + 15, y + 15, x + s - 15, y + s - 15, 0xFFFFFFFF);
        g.fill(x + 20, y + 20, x + s - 20, y + s - 20, 0xFF000000);
    }

    // Рисуем встроенное приложение (квадрат с цветом и символом)
    private void drawBuiltInIcon(IKernelGraphics g, int x, int y, int s, int color, String symbol) {
        // Фон с цветом приложения
        g.fill(x, y, x + s, y + s, color);
        // Рамка
        g.fill(x, y, x + s, y + 2, 0xFF000000); // Top
        g.fill(x, y + s - 2, x + s, y + s, 0xFF000000); // Bottom
        g.fill(x, y, x + 2, y + s, 0xFF000000); // Left
        g.fill(x + s - 2, y, x + s, y + s, 0xFF000000); // Right
        // Символ (просто текст)
        g.drawCenteredString(symbol, x + s/2, y + s/2 - 8, 0xFFFFFFFF);
    }
}