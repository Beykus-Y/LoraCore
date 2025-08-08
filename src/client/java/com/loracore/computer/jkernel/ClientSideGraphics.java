package com.loracore.computer.jkernel;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.service.ClientFont;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;

public class ClientSideGraphics implements IKernelGraphics {
    private final NativeImage screenImage;
    private final ClientFont clientFont;

    public ClientSideGraphics(NativeImage screenImage, ResourceManager resourceManager) {
        this.screenImage = screenImage;
        this.clientFont = new ClientFont(resourceManager);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        // Перевод ARGB (MC) -> ABGR (NativeImage)
        int abgr = (color & 0xFF000000)
                | ((color & 0x00FF0000) >> 16)
                | (color & 0x0000FF00)
                | ((color & 0x000000FF) << 16);
        int width = Math.max(0, x2 - x1);
        int height = Math.max(0, y2 - y1);
        int maxX = Math.min(screenImage.getWidth(), x1 + width);
        int maxY = Math.min(screenImage.getHeight(), y1 + height);
        int startX = Math.max(0, x1);
        int startY = Math.max(0, y1);
        for (int y = startY; y < maxY; y++) {
            for (int x = startX; x < maxX; x++) {
                screenImage.setColor(x, y, abgr);
            }
        }
    }

    @Override
    public void drawString(String text, int x, int y, int color) {
        // Используем наш собственный ClientFont для отрисовки текста
        clientFont.drawString(screenImage, x, y, text, color);
    }

    @Override 
    public int getStringWidth(String text) { 
        return text == null ? 0 : text.length() * 7; // Используем ширину символа из ClientFont
    }
    
    @Override 
    public void drawCenteredString(String text, int centerX, int y, int color) { 
        drawString(text, centerX - (getStringWidth(text) / 2), y, color); 
    }
    
    @Override 
    public void beginFrame() {}
    
    @Override 
    public void endFrame() {}
    
    @Override 
    public void flush() {}
    
    @Override 
    public void pushMatrix() {}
    
    @Override 
    public void popMatrix() {}
    
    @Override 
    public void translate(double x, double y, double z) {}
    
    @Override 
    public void enableScissor(int x, int y, int w, int h) {}
    
    @Override 
    public void disableScissor() {}

    // ===== РЕАЛИЗАЦИЯ НИЗКОУРОВНЕВЫХ МЕТОДОВ =====

    @Override
    public void setPixel(int x, int y, int color) {
        if (x >= 0 && x < screenImage.getWidth() && y >= 0 && y < screenImage.getHeight()) {
            // Конвертируем стандартный ARGB в ABGR, который использует NativeImage
            int abgr = (color & 0xFF000000) | ((color & 0x00FF0000) >> 16) | (color & 0x0000FF00) | ((color & 0x000000FF) << 16);
            screenImage.setColor(x, y, abgr);
        }
    }

    @Override
    public int getPixel(int x, int y) {
        if (x >= 0 && x < screenImage.getWidth() && y >= 0 && y < screenImage.getHeight()) {
            int abgr = screenImage.getColor(x, y);
            // Конвертируем обратно из ABGR в ARGB для пользователя API
            return (abgr & 0xFF000000) | ((abgr & 0x00FF0000) >> 16) | (abgr & 0x0000FF00) | ((abgr & 0x000000FF) << 16);
        }
        return 0; // Возвращаем черный цвет, если вышли за пределы
    }

    @Override
    public int getWidth() {
        return screenImage.getWidth();
    }

    @Override
    public int getHeight() {
        return screenImage.getHeight();
    }
}
