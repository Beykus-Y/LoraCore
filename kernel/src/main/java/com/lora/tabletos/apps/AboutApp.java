package com.lora.tabletos.apps;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;

/**
 * Приложение "About" для отображения информации о системе.
 */
public class AboutApp implements IApplication {
    
    private IApplicationApi api;
    private String tabletUuid = "Loading...";
    private String fsUuid = "Loading...";
    
    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        // UUID будут обновляться из метрик в onRender
    }
    
    @Override
    public void onResume() {
    }
    
    @Override
    public void onPause() {
        // Ничего не делаем при паузе
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] screenSize = api.getScreenSize();
        int width = screenSize[0];
        int height = screenSize[1];
        
        // Очищаем экран темно-синим цветом
        g.fill(0, 0, width, height, 0xFF1A1A2E);
        
        // Заголовок - большим текстом
        int titleY = 60;
        g.drawString("LoraCore OS", 40, titleY, 0xFFFFFFFF, 2.0f);
        g.drawString("v1.3.0", 40, titleY + 40, 0xFF4CAF50, 1.5f);
        
        // Разделитель
        g.fill(40, titleY + 80, width - 40, titleY + 81, 0xFF888888);
        
        int infoY = titleY + 120;
        int lineHeight = 30;
        
        // Получаем UUID из метрик (обновляются с сервера)
        this.tabletUuid = api.getTabletUuidStr();
        this.fsUuid = api.getFsUuidStr();
        
        g.drawString("Tablet UUID:", 40, infoY, 0xFFCCCCCC);
        g.drawString(this.tabletUuid, 200, infoY, 0xFFFFFFFF);
        
        infoY += lineHeight;
        g.drawString("FileSystem UUID:", 40, infoY, 0xFFCCCCCC);
        g.drawString(this.fsUuid, 200, infoY, 0xFFFFFFFF);
        
        infoY += lineHeight + 20;
        
        double uptimeSeconds = api.getSystemMetrics().getOrDefault("uptime", 0.0);
        long uptimeLong = (long) uptimeSeconds;
        int hours = (int)(uptimeLong / 3600);
        int minutes = (int)((uptimeLong % 3600) / 60);
        int seconds = (int)(uptimeLong % 60);
        String uptimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        
        g.drawString("Kernel Uptime:", 40, infoY, 0xFFCCCCCC);
        g.drawString(uptimeStr, 200, infoY, 0xFFFFFFFF);
        
        // Дополнительная информация
        infoY += lineHeight + 30;
        g.drawString("Built with Java 21", 40, infoY, 0xFF888888);
        infoY += lineHeight;
        g.drawString("Fabric Mod Loader", 40, infoY, 0xFF888888);
        infoY += lineHeight;
        g.drawString("Minecraft 1.20.6", 40, infoY, 0xFF888888);
    }
    
    @Override
    public boolean onEvent(KernelEvent event) {
        // Приложение не обрабатывает события
        return false;
    }
    
    @Override
    public void onClose() {
        // Ничего не делаем при закрытии
    }
}
