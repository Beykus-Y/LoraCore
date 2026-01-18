package com.lora.tabletos.core;

import com.loracore.api.ClientApi;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.IKernelVfs;
import com.lora.tabletos.ui.system.NotificationManager;
import java.util.concurrent.CompletableFuture;

/**
 * Реализация IApplicationApi, которая предоставляет безопасный доступ к системным ресурсам.
 */
public class ApplicationApiImpl implements IApplicationApi {
    
    private final IKernelApi kernelApi;
    private final NotificationManager notificationManager;
    public ApplicationApiImpl(IKernelApi kernelApi, NotificationManager notificationManager) {
        this.kernelApi = kernelApi;
        this.notificationManager = notificationManager;
    }
    
    @Override
    public IKernelVfs getVfs() {
        return kernelApi.getVfs();
    }
    
    @Override
    public IKernelGraphics getGraphics() {
        return kernelApi.getGraphics();
    }
    
    @Override
    public CompletableFuture<String> askAI(String prompt) {
        // TODO: Реализовать интеграцию с ИИ API
        // Пока возвращаем заглушку
        return CompletableFuture.completedFuture("AI Assistant: " + prompt);
    }

    @Override
    public int[] getScreenSize() {
        return new int[]{960, 540};
    }
    
    

    // [НОВЫЙ МЕТОД]
    @Override
    public void runOnRenderThread(Runnable task) {
        // Мы используем существующий API-мост для выполнения задачи
        ClientApi.executeOnRenderThread(task);
    }
    /**
     * [НОВАЯ РЕАЛИЗАЦИЯ]
     * Этот метод теперь просто делегирует вызов основному API ядра.
     */
    @Override
    public CompletableFuture<Object[]> invokeDevice(String deviceType, String methodName, Object... args) {
        return kernelApi.invokeDevice(deviceType, methodName, args);
    }

    @Override
    public void showNotification(String message, boolean isError) {
        if (isError) {
            notificationManager.showError(message);
        } else {
            notificationManager.showInfo(message);
        }
    }
    
    @Override
    public java.util.Map<String, Double> getSystemMetrics() {
        // Делегируем вызов основному API ядра
        return kernelApi.getSystemMetrics();
    }
    
    @Override
    public String getTabletUuidStr() {
        return kernelApi.getTabletUuidStr();
    }
    
    @Override
    public String getFsUuidStr() {
        return kernelApi.getFsUuidStr();
    }
}
