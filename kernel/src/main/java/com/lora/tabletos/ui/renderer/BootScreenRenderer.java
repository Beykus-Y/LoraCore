package com.lora.tabletos.ui.renderer;

import com.loracore.computer.kernel.IKernelGraphics;
import com.lora.tabletos.state.KernelState;
import com.lora.tabletos.util.GraphicsUtils;

/**
 * Рендерер экрана загрузки.
 */
public class BootScreenRenderer {
    
    private static final int GRADIENT_START_COLOR = 0xFF1a1a2e;
    private static final int GRADIENT_END_COLOR = 0xFF16213e;
    private static final int LOGO_COLOR = 0xFF4A90E2;
    private static final int LOGO_LETTER_COLOR = 0xFFFFFFFF;
    
    /**
     * Рисует экран загрузки.
     */
    public static void render(IKernelGraphics graphics, KernelState state, String statusMessage, String panicMessage) {
        // Рисуем градиентный фон
        GraphicsUtils.drawGradientBackground(graphics, 480, 270, GRADIENT_START_COLOR, GRADIENT_END_COLOR);
        
        // Рисуем логотип
        drawLogo(graphics);
        
        // Рисуем заголовок
        GraphicsUtils.drawCenteredText(graphics, state.getDisplayName(), 240, 115, state.getTitleColor());
        
        // Рисуем детальный статус
        GraphicsUtils.drawCenteredText(graphics, statusMessage, 240, 135, 0xFFCCCCCC);
        
        // Рисуем сообщение об ошибке, если есть
        if (state == KernelState.KERNEL_PANIC && panicMessage != null) {
            GraphicsUtils.drawCenteredText(graphics, panicMessage, 240, 155, 0xFFFFAAAA);
        }
        
        // Добавляем индикатор загрузки для состояний загрузки
        if (state.isBootState()) {
            drawLoadingIndicator(graphics);
        }
    }
    
    /**
     * Рисует логотип LoraOS.
     */
    private static void drawLogo(IKernelGraphics graphics) {
        int centerX = 240;
        int centerY = 60;
        int size = 40;
        
        // Рисуем круг логотипа
        graphics.fill(centerX - size/2, centerY - size/2, centerX + size/2, centerY + size/2, LOGO_COLOR);
        
        // Рисуем букву "L" в логотипе
        graphics.fill(centerX - 8, centerY - 12, centerX - 4, centerY + 12, LOGO_LETTER_COLOR);
        graphics.fill(centerX - 8, centerY + 8, centerX + 4, centerY + 12, LOGO_LETTER_COLOR);
        
        // Рисуем букву "O" в логотипе
        graphics.fill(centerX + 6, centerY - 8, centerX + 14, centerY - 4, LOGO_LETTER_COLOR);
        graphics.fill(centerX + 6, centerY + 4, centerX + 14, centerY + 8, LOGO_LETTER_COLOR);
        graphics.fill(centerX + 6, centerY - 4, centerX + 10, centerY + 4, LOGO_LETTER_COLOR);
        graphics.fill(centerX + 10, centerY - 4, centerX + 14, centerY + 4, LOGO_LETTER_COLOR);
    }
    
    /**
     * Рисует анимированный индикатор загрузки.
     */
    private static void drawLoadingIndicator(IKernelGraphics graphics) {
        int centerX = 240;
        int centerY = 200;
        int radius = 20;
        
        // Анимируем индикатор на основе времени
        long time = System.currentTimeMillis();
        float angle = (time % 2000) / 2000.0f * 2 * (float) Math.PI;
        
        // Рисуем круговой индикатор с более плавной анимацией
        for (int i = 0; i < 12; i++) {
            float dotAngle = angle + i * (2 * (float) Math.PI / 12);
            int x = centerX + (int) (Math.cos(dotAngle) * radius);
            int y = centerY + (int) (Math.sin(dotAngle) * radius);
            
            // Прозрачность зависит от позиции и времени
            float alpha = 0.2f + 0.8f * (float) Math.sin(dotAngle - angle + Math.PI);
            int alphaValue = (int) (alpha * 255);
            
            // Цвет зависит от состояния
            int baseColor = 0xFFFFFFFF; // Белый по умолчанию
            
            // Применяем прозрачность к базовому цвету
            int r = (baseColor >> 16) & 0xFF;
            int g = (baseColor >> 8) & 0xFF;
            int b = baseColor & 0xFF;
            
            int color = 0xFF000000 | ((int)(r * alpha) << 16) | ((int)(g * alpha) << 8) | (int)(b * alpha);
            
            // Размер точки зависит от прозрачности
            int dotSize = (int) (2 + alpha * 2);
            graphics.fill(x - dotSize, y - dotSize, x + dotSize, y + dotSize, color);
        }
    }
}
