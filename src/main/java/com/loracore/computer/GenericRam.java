package com.loracore.computer;

/**
 * Простая реализация ОЗУ как устройства памяти.
 * Использует массив байтов фиксированного размера.
 * Zero-allocation: все операции работают напрямую с массивом.
 */
public class GenericRam implements IMemoryMappedDevice {
    private final byte[] memory;

    public GenericRam(int sizeBytes) {
        this.memory = new byte[sizeBytes];
    }

    @Override
    public int getSize() {
        return memory.length;
    }

    @Override
    public byte read(int offset) {
        // SystemBus уже проверил границы, но для безопасности оставляем проверку
        if (offset < 0 || offset >= memory.length) {
            return 0;
        }
        return memory[offset];
    }

    @Override
    public void write(int offset, byte value) {
        if (offset < 0 || offset >= memory.length) {
            return; // Игнорируем выход за границы
        }
        memory[offset] = value;
    }

    /**
     * Прямой доступ к массиву памяти (для performBitFlip и других низкоуровневых операций).
     * ОПАСНО: Используйте только для аппаратных операций (bit flips, DMA).
     */
    public byte[] getRawMemory() {
        return memory;
    }
}
