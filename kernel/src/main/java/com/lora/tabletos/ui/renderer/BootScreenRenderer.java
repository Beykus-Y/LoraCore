package com.lora.tabletos.ui.renderer;

import com.loracore.computer.kernel.IKernelGraphics;
import com.lora.tabletos.state.KernelState;
import com.lora.tabletos.util.ColorUtils;

public class BootScreenRenderer {

    // --- Цветовая палитра ---
    private static final int COLOR_BG_TOP = 0xFF0F172A;      // Глубокий синий
    private static final int COLOR_BG_BOTTOM = 0xFF020617;   // Почти черный
    private static final int COLOR_ACCENT = 0xFF3B82F6;      // Яркий синий (Lapis/Tech)
    private static final int COLOR_TEXT = 0xFFE2E8F0;        // Светло-серый
    private static final int COLOR_TEXT_DIM = 0xFF94A3B8;    // Тусклый серый
    private static final int COLOR_SCANLINE = 0x10FFFFFF;    // Очень слабая белая полоса

    // Палитра для Kernel Panic
    private static final int COLOR_PANIC_BG = 0xFF450A0A;    // Темно-красный
    private static final int COLOR_PANIC_TEXT = 0xFFFECACA;  // Светло-красный

    public static void render(IKernelGraphics g, KernelState state, String statusMessage, String panicMessage) {
        int w = g.getWidth();
        int h = g.getHeight();
        int centerX = w / 2;
        int centerY = h / 2;
        long time = System.currentTimeMillis();

        // 1. Отрисовка фона
        if (state == KernelState.KERNEL_PANIC) {
            drawPanicBackground(g, w, h, time);
        } else {
            drawModernBackground(g, w, h, time);
        }

        // 2. Отрисовка Логотипа (пульсирующий)
        // Амплитуда пульсации: от 0.95 до 1.05
        float pulse = 1.0f + (float) Math.sin(time / 500.0) * 0.05f;
        drawLogo(g, centerX, centerY - 60, pulse, state == KernelState.KERNEL_PANIC);

        // 3. Отрисовка Текста
        drawStatusText(g, centerX, centerY, state, statusMessage, panicMessage);

        // 4. Индикатор загрузки (только если не паника и не зависание)
        if (state.isBootState()) {
            drawOrbitalLoader(g, centerX, centerY + 90, time);
        }
    }

    /**
     * Рисует современный градиентный фон с эффектом сканлайна.
     */
    private static void drawModernBackground(IKernelGraphics g, int w, int h, long time) {
        // Градиент сверху вниз (симуляция через полосы для производительности)
        int steps = 20;
        int stepH = (h / steps) + 1;
        for (int i = 0; i < steps; i++) {
            float progress = (float) i / steps;
            int color = ColorUtils.interpolateColor(COLOR_BG_TOP, COLOR_BG_BOTTOM, progress);
            g.fill(0, i * stepH, w, (i + 1) * stepH, color);
        }

        // Эффект сканлайна (бегущая полоса)
        int scanlineY = (int) ((time / 10) % (h + 100)) - 50;
        int scanlineH = 40; // Высота полосы
        if (scanlineY > -scanlineH && scanlineY < h) {
            // Рисуем полупрозрачную полосу
            g.fill(0, scanlineY, w, scanlineY + scanlineH, COLOR_SCANLINE);
        }
    }

    /**
     * Рисует тревожный фон для экрана смерти.
     */
    private static void drawPanicBackground(IKernelGraphics g, int w, int h, long time) {
        // Мерцающий красный фон
        float flash = (float) Math.abs(Math.sin(time / 200.0));
        int color = ColorUtils.interpolateColor(COLOR_PANIC_BG, 0xFF000000, flash * 0.3f);
        g.fill(0, 0, w, h, color);

        // "Глитч" полоски
        if (Math.random() > 0.9) {
            int y = (int)(Math.random() * h);
            g.fill(0, y, w, y + 2, 0xFFFF0000);
        }
    }

    /**
     * Рисует логотип LoraOS.
     */
    private static void drawLogo(IKernelGraphics g, int x, int y, float scale, boolean isPanic) {
        int baseSize = 80;
        int size = (int) (baseSize * scale);
        int halfSize = size / 2;

        int logoColor = isPanic ? 0xFFFF0000 : COLOR_ACCENT;
        int letterColor = 0xFFFFFFFF;

        // Внешний круг (рисование через заполнение прямоугольника с проверкой радиуса - дорого,
        // но для логотипа один раз в кадр сойдет. Или используем примитив квадрата, если нет круга)
        // Здесь используем упрощенный стиль: квадрат со скруглением (эмуляция)

        // Тень
        g.fill(x - halfSize + 4, y - halfSize + 4, x + halfSize + 4, y + halfSize + 4, 0x40000000);
        // Основа
        g.fill(x - halfSize, y - halfSize, x + halfSize, y + halfSize, logoColor);

        // Буква "L" (масштабируется вместе с логотипом)
        int thickness = (int)(12 * scale);
        int lHeight = (int)(40 * scale);
        int lWidth = (int)(30 * scale);
        int lx = x - (lWidth / 2) + (int)(5 * scale); // Центровка визуально
        int ly = y - (lHeight / 2);

        // Вертикальная часть L
        g.fill(lx, ly, lx + thickness, ly + lHeight, letterColor);
        // Горизонтальная часть L
        g.fill(lx, ly + lHeight - thickness, lx + lWidth, ly + lHeight, letterColor);
    }

    /**
     * Рисует тексты статуса.
     */
    private static void drawStatusText(IKernelGraphics g, int cx, int cy, KernelState state, String msg, String panicMsg) {
        // Заголовок состояния
        String title = state.getDisplayName();
        if (state == KernelState.BOOTING) title = "LoraOS Bootloader v2.1";

        g.drawCenteredString(title, cx, cy + 20, state.getTitleColor());

        // Основное сообщение (статус)
        if (msg != null && !msg.isEmpty()) {
            g.drawCenteredString(msg, cx, cy + 45, COLOR_TEXT_DIM);
        }

        // Сообщение об ошибке (Panic)
        if (state == KernelState.KERNEL_PANIC && panicMsg != null) {
            // Отрисовка рамки для ошибки
            int textWidth = g.getStringWidth(panicMsg);
            int boxW = Math.max(300, textWidth + 40);
            int boxH = 60;
            int bx = cx - boxW / 2;
            int by = cy + 70;

            g.fill(bx, by, bx + boxW, by + boxH, 0x80000000); // Полупрозрачный черный фон
            g.fill(bx, by, bx + 2, by + boxH, 0xFFFF5555);   // Красная полоска слева

            g.drawCenteredString("CRITICAL ERROR:", cx, by + 10, COLOR_PANIC_TEXT);
            g.drawCenteredString(panicMsg, cx, by + 30, 0xFFFFFFFF);
        }
    }

    /**
     * Рисует красивый орбитальный индикатор загрузки.
     */
    private static void drawOrbitalLoader(IKernelGraphics g, int cx, int cy, long time) {
        int radius = 25;
        int dots = 8;
        double speed = 0.005; // Скорость вращения

        // Рисуем шлейф
        for (int i = 0; i < dots; i++) {
            // Угол со смещением для каждой точки
            double angle = (time * speed) - (i * 0.4); // 0.4 - расстояние между точками в радианах

            // Координаты
            int dx = cx + (int) (Math.cos(angle) * radius);
            int dy = cy + (int) (Math.sin(angle) * radius);

            // Прозрачность падает для дальних точек (шлейф)
            float alpha = 1.0f - ((float)i / dots);
            if (alpha < 0) alpha = 0;

            // Цвет с учетом прозрачности
            int color = ColorUtils.withAlpha(COLOR_ACCENT, (int)(alpha * 255));

            // Размер точки уменьшается к хвосту
            int size = (i == 0) ? 5 : (i < 3 ? 4 : 3);
            int offset = size / 2;

            g.fill(dx - offset, dy - offset, dx + offset, dy + offset, color);
        }

        // Маленькая точка в центре (ядро)
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x80FFFFFF);
    }
}