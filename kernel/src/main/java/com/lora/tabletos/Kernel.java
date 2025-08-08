// Полный файл: kernel/src/main/java/com/lora/tabletos/Kernel.java

package com.lora.tabletos;

import com.loracore.computer.kernel.IKernel;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

public class Kernel implements IKernel {

    /**
     * Вызывается один раз при загрузке.
     */
    @Override
    public void onBoot(IKernelApi api) {
        // Получаем доступ к графическому API
        IKernelGraphics gpu = api.getGraphics();

        // --- РИСУЕМ ОДИН РАЗ И НАВСЕГДА ---

        // 1. Заливаем фон темно-синим цветом
        gpu.fill(0, 0, 480, 270, 0x0000AA);

        // 2. Рисуем рамку
        gpu.fill(5, 5, 475, 265, 0xAAAAAA);
        gpu.fill(6, 6, 474, 264, 0x555555);

        // 3. Рисуем текст
        gpu.drawString("STATIC RENDER TEST", 15, 15, 0xFFFF55); // Желтый
        gpu.drawString("If you see this, the core render pipeline works.", 15, 30, 0xFFFFFF); // Белый
    }

    /**
     * Этот метод теперь намеренно пустой. Мы не хотим отправлять
     * команды каждый кадр, чтобы проверить базовую функциональность.
     */
    @Override
    public void onRender(Object drawContext, int mouseX, int mouseY, float delta) {
        // НЕ ДЕЛАЕМ НИЧЕГО
    }

    // Остальные методы можно оставить пустыми
    @Override
    public void onTick() {}

    @Override
    public void onEvent(KernelEvent event) {}

    @Override
    public void onShutdown() {}
}