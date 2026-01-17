package com.loracore.computer;

/**
 * Интерфейс для устройств, подключенных к системной шине через MMIO.
 * Все устройства должны реализовывать этот интерфейс для работы с адресным пространством.
 */
public interface IMemoryMappedDevice {
    /**
     * Возвращает размер устройства в байтах (адресное пространство).
     */
    int getSize();

    /**
     * Чтение байта по смещению относительно начала устройства.
     */
    byte read(int offset);

    /**
     * Запись байта по смещению.
     */
    void write(int offset, byte value);

    /**
     * Чтение 32-битного целого числа (little-endian).
     */
    default int readInt(int offset) {
        return ((read(offset) & 0xFF) |
                ((read(offset + 1) & 0xFF) << 8) |
                ((read(offset + 2) & 0xFF) << 16) |
                ((read(offset + 3) & 0xFF) << 24));
    }

    /**
     * Запись 32-битного целого числа (little-endian).
     */
    default void writeInt(int offset, int value) {
        write(offset, (byte)(value & 0xFF));
        write(offset + 1, (byte)((value >> 8) & 0xFF));
        write(offset + 2, (byte)((value >> 16) & 0xFF));
        write(offset + 3, (byte)((value >> 24) & 0xFF));
    }
}
