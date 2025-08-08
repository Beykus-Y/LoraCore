package com.lora.tabletos.util;

import com.loracore.computer.kernel.IKernelGraphics;

/**
 * Утилиты для работы с графикой.
 */
public final class GraphicsUtils {
    
    private GraphicsUtils() {
        // Утилитный класс
    }
    
    /**
     * Рисует градиентный фон.
     * 
     * @param graphics Графический контекст
     * @param width Ширина
     * @param height Высота
     * @param color1 Начальный цвет
     * @param color2 Конечный цвет
     */
    public static void drawGradientBackground(IKernelGraphics graphics, int width, int height, int color1, int color2) {
        for (int y = 0; y < height; y++) {
            float progress = (float) y / height;
            int color = ColorUtils.interpolateColor(color1, color2, progress);
            graphics.fill(0, y, width, y + 1, color);
        }
    }
    
    /**
     * Рисует центрированный текст.
     * 
     * @param graphics Графический контекст
     * @param text Текст для отрисовки
     * @param centerX Центральная X координата
     * @param y Y координата
     * @param color Цвет текста
     */
    public static void drawCenteredText(IKernelGraphics graphics, String text, int centerX, int y, int color) {
        int textWidth = graphics.getStringWidth(text);
        graphics.drawString(text, centerX - textWidth / 2, y, color);
    }
    
    /**
     * Рисует прямоугольник с закругленными углами (упрощенная версия).
     * 
     * @param graphics Графический контекст
     * @param x X координата
     * @param y Y координата
     * @param width Ширина
     * @param height Высота
     * @param color Цвет
     */
    public static void drawRoundedRect(IKernelGraphics graphics, int x, int y, int width, int height, int color) {
        // Упрощенная версия - просто прямоугольник
        graphics.fill(x, y, x + width, y + height, color);
    }
    
    /**
     * Рисует круг.
     * 
     * @param graphics Графический контекст
     * @param centerX Центральная X координата
     * @param centerY Центральная Y координата
     * @param radius Радиус
     * @param color Цвет
     */
    public static void drawCircle(IKernelGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }
}
