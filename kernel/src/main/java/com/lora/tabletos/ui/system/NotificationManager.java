package com.lora.tabletos.ui.system;

import com.loracore.computer.kernel.IKernelGraphics;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    private static final int DISPLAY_TIME = 3000; // 3 секунды
    private static final int FADE_TIME = 500; // 0.5 секунды на анимацию

    private record Notification(String message, int color, long timestamp) {}
    private final List<Notification> notifications = new ArrayList<>();

    public void show(String message, int color) {
        notifications.add(new Notification(message, color, System.currentTimeMillis()));
    }

    public void showInfo(String message) {
        show(message, 0xFF2196F3); // Синий
    }

    public void showError(String message) {
        show(message, 0xFFF44336); // Красный
    }

    public void render(IKernelGraphics g, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        int y = screenHeight - 60; // Начинаем снизу, над доком

        Iterator<Notification> it = notifications.iterator();
        while (it.hasNext()) {
            Notification n = it.next();
            long age = now - n.timestamp();

            if (age > DISPLAY_TIME + FADE_TIME) {
                it.remove();
                continue;
            }

            // Расчет прозрачности
            int alpha = 255;
            if (age < FADE_TIME) { // Появление
                alpha = (int) ((age / (float) FADE_TIME) * 255);
            } else if (age > DISPLAY_TIME) { // Исчезновение
                alpha = (int) ((1.0f - ((age - DISPLAY_TIME) / (float) FADE_TIME)) * 255);
            }

            // Фон уведомления (Темно-серый с прозрачностью)
            int bgColor = (alpha << 24) | 0x333333;
            int textColor = (alpha << 24) | (n.color & 0x00FFFFFF);

            int textWidth = g.getStringWidth(n.message);
            int padding = 10;
            int boxWidth = textWidth + (padding * 2);
            int x = (screenWidth - boxWidth) / 2;

            // Рисуем плашку
            g.fill(x, y, x + boxWidth, y + 20, bgColor);
            // Рисуем текст
            g.drawCenteredString(n.message, screenWidth / 2, y + 6, textColor);

            y -= 25; // Сдвигаем следующее уведомление вверх
        }
    }
}