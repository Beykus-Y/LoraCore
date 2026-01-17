package com.loracore.computer;

/**
 * Исключение, выбрасываемое при аппаратных ошибках (выход за границы памяти, нестабильность).
 * Должно ловиться VirtualMachine и приводить к Kernel Panic.
 */
public class HardwareInterruptException extends RuntimeException {
    public HardwareInterruptException(String message) {
        super(message);
    }
}
