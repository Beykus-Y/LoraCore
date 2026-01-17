package com.loracore.computer.jkernel;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.network.graphics.GpuCommand;

import java.util.UUID;

public class ServerSideGraphics implements IKernelGraphics {
    private final UUID tabletUuid;
    
    public ServerSideGraphics(UUID tabletUuid) { 
        this.tabletUuid = tabletUuid; 
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        // Отправляем команду на сервер через GPU API
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) return;
        com.loracore.api.GpuApi.sendCommand(this.tabletUuid, new GpuCommand.Fill(x1, y1, width, height, color));
    }

    @Override
    public void drawString(String text, int x, int y, int color) {
        // Старый метод
        com.loracore.api.GpuApi.sendCommand(this.tabletUuid, new GpuCommand.DrawText(x, y, text, color));
    }

    @Override
    public void drawString(String text, int x, int y, int color, float scale) {
        // Новый метод. Пока что ИГНОРИРУЕМ scale для сетевой отрисовки,
        // чтобы код скомпилировался. Текст будет обычного размера.
        // TODO: Добавить поддержку scale в GpuCommand.DrawText
        drawString(text, x, y, color);
    }

    @Override 
    public int getStringWidth(String text) { 
        return text.length() * 6; 
    }
    
    @Override 
    public void beginFrame() {}
    
    @Override 
    public void endFrame() {}
    
    @Override 
    public void flush() {}
    
    @Override 
    public void drawCenteredString(String text, int centerX, int y, int color) { 
        drawString(text, centerX - (getStringWidth(text) / 2), y, color); 
    }
    
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

    // ===== ЗАГЛУШКИ ДЛЯ НИЗКОУРОВНЕВЫХ МЕТОДОВ =====
    @Override 
    public void setPixel(int x, int y, int color) { 
        /* Не реализовано для серверного рендеринга */ 
    }
    
    @Override 
    public int getPixel(int x, int y) { 
        return 0; 
    }
    
    @Override 
    public int getWidth() { 
        return 480; /* Возвращаем стандартный размер */ 
    }
    
    @Override 
    public int getHeight() { 
        return 270; 
    }
}
