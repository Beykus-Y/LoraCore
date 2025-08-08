package com.lora.tabletos.ui.window;

import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Управляет окнами приложений.
 */
public class WindowManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowManager.class);
    private final IKernelApi api;
    
    public WindowManager(IKernelApi api) {
        this.api = api;
    }
    
    /**
     * Рисуем все открытые окна.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        // TODO: Пройтись по списку окон и вызвать у каждого render().
        // Пока что ничего не рисуем, так как окна еще не реализованы
    }
    
    /**
     * Передаем событие активному окну.
     */
    public boolean handleEvent(KernelEvent event) {
        // TODO: Найти активное окно и передать ему событие.
        // if (activeWindow != null) {
        //     return activeWindow.handleEvent(event);
        // }
        return false;
    }
    
    /**
     * Закрываем все окна при выключении.
     */
    public void shutdown() {
        // TODO: Пройтись по списку окон и вызвать у каждого close().
        LOGGER.info("WindowManager shutting down...");
    }
    
    /**
     * Запускает новое приложение.
     * Этот метод будет вызываться из Desktop при клике на иконку.
     */
    public void launchApp(String path) {
        LOGGER.info("WindowManager: Attempting to launch {}", path);
        // TODO: Создать новый ScreenDriver и добавить его в список окон.
        // ScreenDriver newWindow = new ScreenDriver(path, api);
        // windows.add(newWindow);
        // newWindow.launch();
    }
}
