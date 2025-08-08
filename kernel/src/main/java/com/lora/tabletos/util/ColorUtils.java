package com.lora.tabletos.util;

/**
 * Утилиты для работы с цветами в формате ARGB.
 */
public final class ColorUtils {
    
    private ColorUtils() {
        // Утилитный класс
    }
    
    /**
     * Интерполирует между двумя цветами.
     * 
     * @param color1 Первый цвет в формате ARGB
     * @param color2 Второй цвет в формате ARGB
     * @param progress Прогресс интерполяции (0.0 - 1.0)
     * @return Интерполированный цвет
     */
    public static int interpolateColor(int color1, int color2, float progress) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Создает цвет с прозрачностью.
     * 
     * @param alpha Прозрачность (0-255)
     * @param r Красный компонент (0-255)
     * @param g Зеленый компонент (0-255)
     * @param b Синий компонент (0-255)
     * @return Цвет в формате ARGB
     */
    public static int withAlpha(int alpha, int r, int g, int b) {
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    /**
     * Создает цвет с прозрачностью из существующего цвета.
     * 
     * @param color Исходный цвет
     * @param alpha Новая прозрачность (0-255)
     * @return Цвет с новой прозрачностью
     */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
    
    /**
     * Получает красный компонент цвета.
     */
    public static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }
    
    /**
     * Получает зеленый компонент цвета.
     */
    public static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }
    
    /**
     * Получает синий компонент цвета.
     */
    public static int getBlue(int color) {
        return color & 0xFF;
    }
    
    /**
     * Получает альфа-компонент цвета.
     */
    public static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }
}
