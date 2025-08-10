// Файл: device-test-app/src/main/java/com/lora/tabletos/apps/DeviceTestApp.java
package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.core.DeviceManager;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class DeviceTestApp implements IApplication {

    private IApplicationApi api;
    private DeviceManager deviceManager;
    private String statusText = "Press button to test...";

    // В будущем здесь будут виджеты, пока просто рисуем
    private static final int BUTTON_X = 50;
    private static final int BUTTON_Y = 100;
    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 30;

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        // Создаем наш DeviceManager, передавая ему API
        this.deviceManager = new DeviceManager(api);
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, 480, 240, 0xFF1E1E1E); // Фон
        g.drawCenteredString("Device API Test", 240, 30, 0xFFFFFFFF);

        // Рисуем кнопку
        g.fill(BUTTON_X, BUTTON_Y, BUTTON_X + BUTTON_W, BUTTON_Y + BUTTON_H, 0xFF4CAF50);
        g.drawCenteredString("Test Redstone Power", BUTTON_X + BUTTON_W / 2, BUTTON_Y + 11, 0xFFFFFFFF);

        // Рисуем статус
        g.drawCenteredString(statusText, 240, 150, 0xFFCCCCCC);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            // Проверяем, кликнули ли по кнопке
            if (mouseEvent.mouseX >= BUTTON_X && mouseEvent.mouseX <= BUTTON_X + BUTTON_W &&
                    mouseEvent.mouseY >= BUTTON_Y && mouseEvent.mouseY <= BUTTON_Y + BUTTON_H) {

                runTest();
                return true;
            }
        }
        return false;
    }

    private void runTest() {
        statusText = "Requesting power level...";

        // Используем наш DeviceManager для вызова метода!
        deviceManager.getRedstonePower("down")
                .whenComplete((result, error) -> {
                    // ВАЖНО: Обновление UI из асинхронного потока
                    // нужно выполнять через специальный метод API.
                    api.runOnRenderThread(() -> {
                        if (error != null) {
                            statusText = "Error: " + error.getMessage();
                        } else {
                            statusText = "Success! Power level is: " + result;
                        }
                    });
                });
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
    @Override public void onClose() {}
}