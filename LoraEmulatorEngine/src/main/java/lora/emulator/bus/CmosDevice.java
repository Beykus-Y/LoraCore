package lora.emulator.bus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class CmosDevice implements IMemoryMappedDevice {

    private final byte[] memory = new byte[128]; // 128 байт настроек хватит всем
    private final File backingFile;

    public CmosDevice(File file) {
        this.backingFile = file;
        if (file.exists()) {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                System.arraycopy(data, 0, memory, 0, Math.min(data.length, memory.length));
                System.out.println("[CMOS] Settings loaded.");
            } catch (IOException e) {
                System.err.println("[CMOS] Failed to load settings: " + e.getMessage());
            }
        }
    }

    @Override
    public int getSize() {
        return 128; // Маленький размер
    }

    @Override
    public byte read(int offset) {
        return memory[offset];
    }

    @Override
    public void write(int offset, byte value) {
        if (memory[offset] != value) {
            memory[offset] = value;
            save(); // Сохраняем сразу (в реальности так не делают, но мы не хотим потерять данные при краше)
        }
    }

    private void save() {
        try {
            Files.write(backingFile.toPath(), memory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}