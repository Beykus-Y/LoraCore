package lora.emulator.memory;

import lora.emulator.bus.IMemoryMappedDevice;

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
        // Убрана проверка границ для скорости, SystemBus уже проверил offset < getSize()
        return memory[offset];
    }

    @Override
    public void write(int offset, byte value) {
        memory[offset] = value;
    }
}