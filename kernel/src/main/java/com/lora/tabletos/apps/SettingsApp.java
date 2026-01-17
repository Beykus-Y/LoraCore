package com.lora.tabletos.apps;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;

/**
 * Приложение "Settings" - заглушка для будущих настроек системы.
 */
public class SettingsApp implements IApplication {
    
    private IApplicationApi api;
    
    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
    }
    
    @Override
    public void onResume() {
        // Ничего не делаем
    }
    
    @Override
    public void onPause() {
        // Ничего не делаем
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] screenSize = api.getScreenSize();
        int width = screenSize[0];
        int height = screenSize[1];
        
        // Очищаем экран серым цветом
        g.fill(0, 0, width, height, 0xFF2C2C2C);
        
        // Заголовок
        g.drawString("Settings", 40, 40, 0xFFFFFFFF, 1.5f);
        
        // Разделитель
        g.fill(40, 70, width - 40, 71, 0xFF888888);
        
        // Сообщение о том, что настройки пока не реализованы
        int centerY = height / 2;
        g.drawString("Settings panel coming soon", width / 2 - 150, centerY - 20, 0xFFCCCCCC);
        g.drawString("This is a placeholder for future settings", width / 2 - 180, centerY + 20, 0xFF888888);
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
