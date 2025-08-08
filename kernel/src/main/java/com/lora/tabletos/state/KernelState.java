package com.lora.tabletos.state;

/**
 * Определяет этапы жизненного цикла ядра LoraOS.
 */
public enum KernelState {
    /**
     * Начальная загрузка, 1-секундная пауза.
     */
    BOOTING("LoraOS :: Booting", 0xFFFFFFFF),
    
    /**
     * Проверка и ремонт файловой системы.
     */
    CHECKING_SYSTEM("LoraOS :: System Integrity", 0xFF55FFFF),
    
    /**
     * Загрузка иконок и других элементов UI.
     */
    INITIALIZING_UI("LoraOS :: Initializing UI", 0xFFFFFF55),
    
    /**
     * Нормальная работа ОС.
     */
    RUNNING("LoraOS :: Running", 0xFF55FF55),
    
    /**
     * Сбой на этапе проверки системы.
     */
    BOOT_FAILED("BOOT FAILED", 0xFFFF5555),
    
    /**
     * Критический сбой в компоненте UI.
     */
    KERNEL_PANIC("KERNEL PANIC!", 0xFFFF5555);
    
    private final String displayName;
    private final int titleColor;
    
    KernelState(String displayName, int titleColor) {
        this.displayName = displayName;
        this.titleColor = titleColor;
    }
    
    /**
     * Возвращает отображаемое имя состояния.
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Возвращает цвет заголовка для состояния.
     */
    public int getTitleColor() {
        return titleColor;
    }
    
    /**
     * Проверяет, является ли состояние состоянием загрузки.
     */
    public boolean isBootState() {
        return this == BOOTING || this == CHECKING_SYSTEM || this == INITIALIZING_UI;
    }
    
    /**
     * Проверяет, является ли состояние критическим.
     */
    public boolean isCritical() {
        return this == BOOT_FAILED || this == KERNEL_PANIC;
    }
}
