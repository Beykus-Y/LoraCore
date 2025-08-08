package com.lora.tabletos;

/**
 * Главная точка входа для LoraOS Kernel.
 * Этот класс просто делегирует все вызовы к основному ядру в пакете core.
 * 
 * <p>Структура LoraOS:</p>
 * <ul>
 *   <li><strong>core</strong> - Основные компоненты системы (ядро, проверка целостности)</li>
 *   <li><strong>state</strong> - Управление состояниями и жизненным циклом</li>
 *   <li><strong>ui</strong> - Пользовательский интерфейс (десктоп, окна, навигация)</li>
 *   <li><strong>util</strong> - Утилиты и вспомогательные классы</li>
 * </ul>
 */
public class LoraOSKernel extends com.lora.tabletos.core.LoraOSKernel {
    
    /**
     * Создает новый экземпляр LoraOS Kernel.
     */
    public LoraOSKernel() {
        super();
    }
}