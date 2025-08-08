package com.lora.tabletos.core;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

/**
 * Контракт для всех Java-приложений в LoraCore OS.
 * Этот интерфейс предоставляет безопасный API для приложений.
 */
public interface IApplication {
    
    /**
     * Вызывается при запуске приложения.
     * @param api Безопасный API для взаимодействия с системой
     */
    void onLoad(IApplicationApi api);
    
    /**
     * Вызывается каждый кадр для отрисовки интерфейса приложения.
     * @param g Графический контекст
     * @param mouseX Координата X мыши
     * @param mouseY Координата Y мыши
     * @param delta Время между кадрами
     */
    void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta);
    
    /**
     * Вызывается при событиях ввода.
     * @param event Событие ввода
     */
    void onEvent(KernelEvent event);
    
    /**
     * Вызывается при закрытии приложения.
     */
    void onClose();
}
