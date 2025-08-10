// Новый файл: src/client/java/com/loracore/computer/kernel/IKernelGraphics.java
package com.loracore.computer.kernel;

/**
 * Абстрактный графический API, который мод предоставляет ядру.
 * Ядро не знает о DrawContext, оно знает только об этом интерфейсе.
 */
public interface IKernelGraphics {
    void beginFrame();
    void endFrame();
    void fill(int x1, int y1, int x2, int y2, int color);
    void drawCenteredString(String text, int centerX, int y, int color);
    int getStringWidth(String text);
    void drawString(String text, int x, int y, int color);
    void translate(double x, double y, double z);
    /**
     * Принудительно отрисовывает все накопленные в буфере команды.
     * Необходимо вызывать после отрисовки текста, но до отключения scissor.
     */
    void flush();
    void pushMatrix();
    void popMatrix();

    // НОВЫЙ МЕТОД: Включает отсечение. Все последующие операции рисования
    // будут ограничены этим прямоугольником.
    void enableScissor(int x, int y, int width, int height);

    // НОВЫЙ МЕТОД: Выключает отсечение, возвращая рендеринг в полноэкранный режим.
    void disableScissor();

    // ===== НОВЫЕ НИЗКОУРОВНЕВЫЕ МЕТОДЫ =====

    /**
     * Устанавливает цвет одного пикселя.
     * @param x Координата X
     * @param y Координата Y
     * @param color Цвет в формате 0xAARRGGBB
     */
    void setPixel(int x, int y, int color);

    /**
     * Получает цвет одного пикселя.
     * @param x Координата X
     * @param y Координата Y
     * @return Цвет в формате 0xAARRGGBB
     */
    int getPixel(int x, int y);

    /**
     * Возвращает ширину экрана в пикселях.
     */
    int getWidth();

    /**
     * Возвращает высоту экрана в пикселях.
     */
    int getHeight();
}