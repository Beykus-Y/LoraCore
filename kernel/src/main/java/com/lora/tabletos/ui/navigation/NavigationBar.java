package com.lora.tabletos.ui.navigation;

import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Навигационная панель внизу экрана.
 */
public class NavigationBar {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NavigationBar.class);
    private final IKernelApi api;
    private static final int BAR_HEIGHT = 30; // Высота панели
    private static final int BAR_COLOR = 0xFF2C3A47; // Очень темно-серый, почти черный
    
    public NavigationBar(IKernelApi api) {
        this.api = api;
    }
    
    /**
     * Рисуем панель внизу экрана.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int screenHeight = g.getHeight();
        int screenWidth = g.getWidth();
        
        g.fill(0, screenHeight - BAR_HEIGHT, screenWidth, screenHeight, BAR_COLOR);
        
        // TODO: В будущем здесь будет отрисовка трех кнопок (Назад, Домой, Приложения).
    }
    
    /**
     * Обрабатываем клики по панели.
     */
    public boolean handleEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            int screenHeight = api.getGraphics().getHeight();
            
            // Проверяем, был ли клик внутри нашей панели
            if (mouseEvent.mouseY >= screenHeight - BAR_HEIGHT) {
                // TODO: Проверить, по какой из трех кнопок кликнули.
                LOGGER.info("Click on Navigation Bar!");
                return true; // Важно! Мы перехватили событие.
            }
        }
        return false; // Клик был не по нашей панели.
    }
    
    /**
     * Закрываем панель при выключении.
     */
    public void shutdown() {
        LOGGER.info("NavigationBar shutting down...");
    }
}
