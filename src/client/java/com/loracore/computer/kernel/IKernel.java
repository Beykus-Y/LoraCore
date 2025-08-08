// ПУТЬ: src/client/java/com/loracore/computer/kernel/IKernel.java
// ОБРАТИ ВНИМАНИЕ: это КЛИЕНТСКИЙ пакет!

package com.loracore.computer.kernel;

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

    /**
     * Вызывается каждый кадр для отрисовки.
     * @param mouseX координата X мыши
     * @param mouseY координата Y мыши
     * @param delta время между кадрами
     */
    void onRender(int mouseX, int mouseY, float delta);

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

    // НОВЫЕ МЕТОДЫ ДЛЯ RENDERER'А
    /**
     * Возвращает текущее состояние ядра.
     * @return Object - текущее состояние (enum)
     */
    Object getState();

    /**
     * Возвращает результаты проверок системы.
     * @return Map<String, Boolean> - результаты проверок
     */
    java.util.Map<String, Boolean> getCheckResults();
}