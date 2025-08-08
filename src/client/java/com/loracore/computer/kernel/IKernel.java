// ПУТЬ: src/client/java/com/loracore/computer/kernel/IKernel.java
// ОБРАТИ ВНИМАНИЕ: это КЛИЕНТСКИЙ пакет!

package com.loracore.computer.kernel;

import net.minecraft.client.gui.DrawContext;

/**
 * Контракт, который должно реализовывать любое Java-ядро для планшета.
 * Этот интерфейс находится на стороне клиента, так как он напрямую связан с рендером.
 */
public interface IKernel {
    /**
     * Вызывается один раз при загрузке ядра.
     * @param api API для взаимодействия с планшетом и игрой.
     */
    void onBoot(IKernelApi api);


    void onRender(Object drawContext, int mouseX, int mouseY, float delta);

    /**
     * Вызывается каждый игровой тик. Для логики, не связанной с рендером.
     */
    void onTick();

    /**
     * Вызывается при событиях ввода.
     * @param event Объект, содержащий информацию о событии (нажатие клавиши и т.д.).
     */
    void onEvent(KernelEvent event);

    /**
     * Вызывается перед выключением VM.
     */
    void onShutdown();
}