package com.loracore.computer.device;

import com.loracore.computer.IMemoryMappedDevice;
import com.loracore.computer.pnp.PnpEntry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class ConfigurationSpaceDevice implements IMemoryMappedDevice {
    private final byte[] romData;

    public ConfigurationSpaceDevice(List<PnpEntry> entries) {
        // Заголовок (8 байт) + Записи (N * 16 байт)
        int size = 8 + (entries.size() * 16);
        // Выравниваем размер до 4 байт для удобства
        if (size % 4 != 0) size += (4 - (size % 4));

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Header
        buffer.putInt(0x4C4F5241); // Magic "LORA"
        buffer.putInt(entries.size()); // Count

        // Entries
        for (PnpEntry entry : entries) {
            buffer.putInt(entry.deviceType());
            buffer.putInt(entry.baseAddress());
            buffer.putInt(entry.sizeBytes());
            buffer.putInt(entry.interruptLine()); // Reserved/IRQ
        }

        this.romData = buffer.array();
    }

    @Override
    public int getSize() { return 4096; } // Фиксированный размер окна 4КБ (0xFFF000 - 0xFFFFFF)

    @Override
    public byte read(int offset) {
        if (offset >= 0 && offset < romData.length) return romData[offset];
        return 0;
    }

    @Override
    public void write(int offset, byte value) { /* Read Only */ }
}