package com.lora.tabletos.apps;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;

import java.util.Map;

/**
 * Приложение "System Monitor" для отображения метрик системы (CPU, RAM, Disk Queue).
 */
public class SystemMonitorApp implements IApplication {
    
    private IApplicationApi api;
    private double cpuLoad = 0.0;
    private double ramUsedKb = 0.0;
    private double ramTotalKb = 0.0;
    private double diskQueue = 0.0;
    
    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
    }
    
    @Override
    public void onResume() {
        // Метрики обновляются в onRender
    }
    
    @Override
    public void onPause() {
        // Ничего не делаем при паузе
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        // Получаем метрики системы
        Map<String, Double> metrics = api.getSystemMetrics();
        cpuLoad = metrics.getOrDefault("cpu_load", 0.0);
        ramUsedKb = metrics.getOrDefault("ram_used_kb", 0.0);
        ramTotalKb = metrics.getOrDefault("ram_total_kb", 0.0);
        diskQueue = metrics.getOrDefault("disk_queue", 0.0);
        
        int[] screenSize = api.getScreenSize();
        int width = screenSize[0];
        int height = screenSize[1];
        
        // Очищаем экран черным цветом
        g.fill(0, 0, width, height, 0xFF000000);
        
        // Заголовок
        g.drawString("System Monitor", 20, 20, 0xFFFFFFFF);
        // Рисуем линию как тонкий прямоугольник
        g.fill(20, 40, width - 20, 41, 0xFF888888);
        
        int y = 70;
        int barWidth = width - 80;
        int barHeight = 30;
        int barX = 40;
        int labelY = y + 10;
        
        // CPU Bar - используем правильные цвета: зеленый (0xFF4CAF50) или красный (0xFFF44336) если > 0.9
        g.drawString(String.format("CPU Load: %.1f%%", cpuLoad * 100), barX, labelY - 25, 0xFFFFFFFF);
        int cpuColor = cpuLoad > 0.9 ? 0xFFF44336 : 0xFF4CAF50; // Красный если > 90%, иначе зеленый
        drawProgressBar(g, barX, y, barWidth, barHeight, cpuLoad, cpuColor);
        y += 60;
        labelY += 60;
        
        // RAM Bar
        double ramPercent = ramTotalKb > 0 ? ramUsedKb / ramTotalKb : 0.0;
        if (ramPercent < 0.0) {
            ramPercent = 0.0;
        } else if (ramPercent > 1.0) {
            ramPercent = 1.0;
        }
        g.drawString(String.format("RAM: %.0f KB / %.0f KB", ramUsedKb, ramTotalKb), barX, labelY - 25, 0xFFFFFFFF);
        drawProgressBar(g, barX, y, barWidth, barHeight, ramPercent, 0xFF0088FF);
        y += 60;
        labelY += 60;
        
        // Disk Queue
        g.drawString(String.format("Disk Tasks in Queue: %.0f", diskQueue), barX, labelY - 25, 0xFFFFFFFF);
        // Для очереди можно показать визуализацию, если значение большое
        if (diskQueue > 0) {
            double queuePercent = Math.min(diskQueue / 100.0, 1.0); // Нормализуем до 100 задач
            drawProgressBar(g, barX, y, barWidth, barHeight, queuePercent, 0xFFFF8800);
        }
        y += 60;
        labelY += 60;
        
        // Disk Tasks Log (последние 5 задач)
        g.drawString("Recent Disk Tasks:", barX, labelY - 25, 0xFFFFFFFF);
        // Показываем информацию о последних задачах (заглушка, так как нет доступа к логу)
        // В будущем это можно получить через специальный API
        String[] recentTasks = {
            "Task queue size: " + (int)diskQueue,
            "Tasks processed: N/A",
            "Last operation: N/A",
            "Average wait time: N/A",
            "Total operations: N/A"
        };
        
        int taskY = labelY + 10;
        for (int i = 0; i < Math.min(recentTasks.length, 5); i++) {
            g.drawString("  • " + recentTasks[i], barX + 10, taskY, 0xFFCCCCCC);
            taskY += 20;
        }
    }
    
    /**
     * Рисует прогресс-бар.
     * @param g Графический контекст
     * @param x Координата X
     * @param y Координата Y
     * @param width Ширина бара
     * @param height Высота бара
     * @param progress Прогресс от 0.0 до 1.0
     * @param color Цвет заполнения
     */
    private void drawProgressBar(IKernelGraphics g, int x, int y, int width, int height, double progress, int color) {
        // Рамка (рисуем как четыре линии используя fill)
        int x2 = x + width;
        int y2 = y + height;
        int borderColor = 0xFF666666;
        
        // Top
        g.fill(x, y, x2, y + 1, borderColor);
        // Bottom
        g.fill(x, y2 - 1, x2, y2, borderColor);
        // Left
        g.fill(x, y, x + 1, y2, borderColor);
        // Right
        g.fill(x2 - 1, y, x2, y2, borderColor);
        
        // Заполнение (fill принимает координаты x1, y1, x2, y2)
        int fillWidth = (int) (width * progress);
        if (fillWidth > 2) {
            g.fill(x + 1, y + 1, x + fillWidth - 1, y2 - 1, color);
        }
    }
    
    @Override
    public boolean onEvent(KernelEvent event) {
        // Приложение не обрабатывает события - это просто монитор
        return false;
    }
    
    @Override
    public void onClose() {
        // Ничего не делаем при закрытии
    }
}
