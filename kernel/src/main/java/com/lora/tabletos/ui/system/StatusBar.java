package com.lora.tabletos.ui.system;

import com.lora.tabletos.ui.window.WindowManager;
import com.loracore.computer.kernel.IKernelGraphics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StatusBar {
    private static final int HEIGHT = 18;
    private static final int BG_COLOR = 0xFF1a1a1a; // Почти черный
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final WindowManager windowManager;

    public StatusBar(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    public void render(IKernelGraphics g, int width) {
        // Фон
        g.fill(0, 0, width, HEIGHT, BG_COLOR);

        // Время справа
        String time = LocalTime.now().format(TIME_FORMAT);
        int timeWidth = g.getStringWidth(time);
        g.drawString(time, width - timeWidth - 5, 5, TEXT_COLOR);

        // Название текущего приложения по центру
        WindowManager.AppInfo activeApp = windowManager.getActiveAppInfo();
        if (activeApp != null) {
            // Убираем расширение и путь для красивого названия
            String appName = getPrettyName(activeApp.path());
            g.drawCenteredString(appName, width / 2, 5, TEXT_COLOR);
        } else {
            g.drawCenteredString("LoraOS", width / 2, 5, 0xFFAAAAAA);
        }

        // Разделительная линия
        g.fill(0, HEIGHT - 1, width, HEIGHT, 0xFF333333);
    }

    public int getHeight() {
        return HEIGHT;
    }

    private String getPrettyName(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.replace(".jar", "").replace(".lua", "")
                .replace("_", " ").substring(0, 1).toUpperCase()
                + fileName.replace(".jar", "").replace(".lua", "")
                .replace("_", " ").substring(1);
    }
}