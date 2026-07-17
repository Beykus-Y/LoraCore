package com.loracore.computer.pnp;

public record PnpEntry(
        int deviceType, // Уникальный ID типа устройства (1=RAM, 2=GPU, 3=HDD, etc.)
        int baseAddress,
        int sizeBytes,
        int interruptLine // На будущее, если добавите прерывания
) {
    // ID типов устройств
    public static final int TYPE_RAM = 0x01;
    public static final int TYPE_GPU = 0x02;
    public static final int TYPE_STORAGE = 0x03;
    public static final int TYPE_NETWORK = 0x04;
    public static final int TYPE_INPUT = 0x05;
    public static final int TYPE_WORLD = 0x06;
}
