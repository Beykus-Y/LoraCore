package lora.emulator.memory;

import lora.emulator.bus.IMemoryMappedDevice;

public class RamStick implements IMemoryMappedDevice {
    private final byte[] memory;
    private final String label; // Название плашки (например "Corsair 4KB")

    public RamStick(int size, String label) {
        this.memory = new byte[size];
        this.label = label;
    }

    @Override
    public int getSize() {
        return memory.length;
    }

    @Override
    public byte read(int offset) {
        return memory[offset];
    }

    @Override
    public void write(int offset, byte value) {
        memory[offset] = value;
    }

    public String getLabel() { return label; }
}