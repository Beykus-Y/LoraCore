// Полный исправленный файл: src/main/java/com/loracore/service/TabletRenderService.java
package com.loracore.service;

// ИСПРАВЛЕНИЕ: Заменяем импорт на новый класс
import com.loracore.computer.ServerScreenState;

/**
 * Сервис, отвечающий за выполнение команд отрисовки на серверном буфере пикселей.
 * Работает как "GPU" на стороне сервера.
 */
public class TabletRenderService {

    private static final TabletRenderService INSTANCE = new TabletRenderService();
    private final ServerFont font = new ServerFont();

    private TabletRenderService() {}

    public static TabletRenderService getInstance() {
        return INSTANCE;
    }

    // ИСПРАВЛЕНИЕ: Тип аргумента 'screen' изменен на ServerScreenState
    public void processFill(ServerScreenState screen, int x, int y, int width, int height, int color) {
        byte[] buffer = screen.getPixelBuffer();
        if (buffer == null) return;

        byte r = (byte) ((color >> 16) & 0xFF);
        byte g = (byte) ((color >> 8) & 0xFF);
        byte b = (byte) (color & 0xFF);
        byte a = (byte) 255;

        int x1 = Math.max(0, x);
        int y1 = Math.max(0, y);
        int x2 = Math.min(ServerFont.SCREEN_WIDTH, x + width);
        int y2 = Math.min(ServerFont.SCREEN_HEIGHT, y + height);

        for (int j = y1; j < y2; j++) {
            for (int i = x1; i < x2; i++) {
                int index = (j * ServerFont.SCREEN_WIDTH + i) * 4;
                buffer[index] = r;
                buffer[index + 1] = g;
                buffer[index + 2] = b;
                buffer[index + 3] = a;
            }
        }
        screen.markDirty();
    }

    // ИСПРАВЛЕНИЕ: Тип аргумента 'screen' изменен на ServerScreenState
    public void processDrawText(ServerScreenState screen, int x, int y, String text, int color) {
        byte[] buffer = screen.getPixelBuffer();
        if (buffer == null) return;
        font.drawString(buffer, x, y, text, color);
        screen.markDirty();
    }

    // ИСПРАВЛЕНИЕ: Тип аргумента 'screen' изменен на ServerScreenState
    public void processCopy(ServerScreenState screen, int sx, int sy, int w, int h, int dx, int dy) {
        byte[] buffer = screen.getPixelBuffer();
        if (buffer == null) return;

        byte[] copyBuffer = new byte[w * h * 4];

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int srcX = sx + i;
                int srcY = sy + j;
                if (srcX >= 0 && srcX < ServerFont.SCREEN_WIDTH && srcY >= 0 && srcY < ServerFont.SCREEN_HEIGHT) {
                    int srcIndex = (srcY * ServerFont.SCREEN_WIDTH + srcX) * 4;
                    int copyIndex = (j * w + i) * 4;
                    System.arraycopy(buffer, srcIndex, copyBuffer, copyIndex, 4);
                }
            }
        }

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int destX = dx + i;
                int destY = dy + j;
                if (destX >= 0 && destX < ServerFont.SCREEN_WIDTH && destY >= 0 && destY < ServerFont.SCREEN_HEIGHT) {
                    int destIndex = (destY * ServerFont.SCREEN_WIDTH + destX) * 4;
                    int copyIndex = (j * w + i) * 4;
                    System.arraycopy(copyBuffer, copyIndex, buffer, destIndex, 4);
                }
            }
        }
        screen.markDirty();
    }
}