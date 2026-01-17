package lora.emulator.bus;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DiskDevice implements IMemoryMappedDevice, ITickable {

    private final SystemBus bus;
    private final RandomAccessFile diskFile;
    private final int sectorSize = 512;
    private final long totalSectors;

    // Регистры контроллера (Mapped Memory IO)
    // Используем массив int для простоты доступа процессора (32-bit)
    private final int[] registers = new int[8]; // С запасом

    // Карта регистров (индексы массива, не смещения!)
    private static final int REG_STATUS = 0; // [R]  0: Ready, 1: Busy, 2: Error
    private static final int REG_CMD    = 1; // [RW] 1: Read, 2: Write
    private static final int REG_LBA    = 2; // [RW] Logical Block Address
    private static final int REG_ADDR   = 3; // [RW] RAM Target Address
    private static final int REG_ERR    = 4; // [R]  Error Code

    // Внутреннее состояние
    private int cyclesToWait = 0;
    private int activeCommand = 0;
    private static final int CMD_READ  = 1;
    private static final int CMD_WRITE = 2;

    public DiskDevice(SystemBus bus, File imageFile) {
        this.bus = bus;
        try {
            this.diskFile = new RandomAccessFile(imageFile, "rw");
            // Если файл пустой или кривой - фиксим на лету, чтобы не крашить эмулятор
            if (diskFile.length() < sectorSize) {
                diskFile.setLength(1024 * 1024); // 1 MB default
            }
            this.totalSectors = diskFile.length() / sectorSize;
            System.out.println("[Disk] Mounted. Sectors: " + totalSectors);
        } catch (IOException e) {
            throw new RuntimeException("[Disk] FATAL: Failed to open disk image", e);
        }
    }

    @Override
    public int getSize() {
        return 32; // 8 регистров по 4 байта
    }

    // --- ЧТЕНИЕ ---

    @Override
    public int readInt(int offset) {
        // Быстрое чтение регистра (выровненное)
        int idx = offset >>> 2; // offset / 4
        if (idx < registers.length) {
            return registers[idx];
        }
        return 0;
    }

    @Override
    public byte read(int offset) {
        // Fallback для побайтового чтения (для отладки)
        int idx = offset >>> 2;
        if (idx < registers.length) {
            int shift = (offset & 3) * 8; // (offset % 4) * 8
            return (byte) (registers[idx] >>> shift);
        }
        return 0;
    }

    // --- ЗАПИСЬ ---

    @Override
    public void writeInt(int offset, int value) {
        int idx = offset >>> 2;
        if (idx >= registers.length) return;

        // Фильтруем запись в Read-Only регистры (Status, Err)
        // Хотя BIOS может захотеть сбросить статус, но пусть лучше делает это через CMD
        if (idx == REG_STATUS || idx == REG_ERR) {
            // System.out.println("[Disk] WARN: Attempt to write RO register " + idx);
            return;
        }

        registers[idx] = value;

        // Триггер исполнения при записи в CMD
        if (idx == REG_CMD) {
            executeCommand(value);
        }
    }

    @Override
    public void write(int offset, byte value) {
        // Поддержка записи байтов (хак для merge в int)
        int idx = offset >>> 2;
        if (idx >= registers.length) return;

        if (idx == REG_STATUS || idx == REG_ERR) return;

        int shift = (offset & 3) * 8;
        int mask = ~(0xFF << shift);
        registers[idx] = (registers[idx] & mask) | ((value & 0xFF) << shift);

        // Если это был последний байт регистра CMD (offset % 4 == 3), можно триггерить.
        // Но для надежности триггерим при любой записи в байты CMD, если значение валидное.
        // В идеале CPU пишет CMD через ST (32-bit), поэтому writeInt важнее.
        if (idx == REG_CMD) {
            // Ждем пока соберут весь int? Нет, считаем, что BIOS знает что делает.
            if (registers[REG_CMD] != 0) {
                executeCommand(registers[REG_CMD]);
            }
        }
    }

    private void executeCommand(int cmd) {
        if (registers[REG_STATUS] == 1) {
            System.err.println("[Disk] Ignored CMD " + cmd + " (Device Busy)");
            return;
        }

        if (cmd == 0) return; // Reset command

        this.activeCommand = cmd;
        this.registers[REG_STATUS] = 1; // BUSY
        this.registers[REG_ERR] = 0;    // Clear Errors
        this.registers[REG_CMD] = 0;    // Clear CMD register immediately (Auto-reset)

        // Симуляция задержки (Latency)
        // Чтение - быстрее, запись - медленнее
        this.cyclesToWait = (cmd == CMD_WRITE) ? 1000 : 500;

        System.out.printf("[Disk] Started CMD %d (LBA: %d, RAM: 0x%X)\n",
                activeCommand, registers[REG_LBA], registers[REG_ADDR]);
    }

    @Override
    public void tick(long cycles) {
        if (registers[REG_STATUS] != 1) return;

        cyclesToWait -= cycles;
        if (cyclesToWait <= 0) {
            finalizeOperation();
        }
    }

    private void finalizeOperation() {
        int lba = registers[REG_LBA];
        int ramAddr = registers[REG_ADDR];

        // 1. Валидация
        if (lba < 0 || lba >= totalSectors) {
            fail(2); // Error 2: OOB
            System.err.println("[Disk] Error: LBA " + lba + " is out of bounds");
            return;
        }

        try {
            if (activeCommand == CMD_READ) {
                // Disk -> RAM
                diskFile.seek((long) lba * sectorSize);
                byte[] buffer = new byte[sectorSize];
                diskFile.readFully(buffer);

                // Пишем в шину
                // Оптимизация: можно было бы добавить writeBlock в SystemBus, но пока так
                for (int i = 0; i < sectorSize; i++) {
                    bus.writeByte(ramAddr + i, buffer[i]);
                }
                // System.out.println("[Disk] Read Complete.");

            } else if (activeCommand == CMD_WRITE) {
                // RAM -> Disk
                byte[] buffer = new byte[sectorSize];
                for (int i = 0; i < sectorSize; i++) {
                    buffer[i] = bus.readByte(ramAddr + i);
                }

                diskFile.seek((long) lba * sectorSize);
                diskFile.write(buffer);
                // System.out.println("[Disk] Write Complete.");
            } else {
                fail(1); // Unknown CMD
                return;
            }

            // Success
            registers[REG_STATUS] = 0; // READY

        } catch (IOException e) {
            e.printStackTrace();
            fail(3); // IO Error
        }
    }

    private void fail(int errorCode) {
        registers[REG_ERR] = errorCode;
        registers[REG_STATUS] = 2; // ERROR STATE
    }
}