package com.loracore.computer.device;

import com.loracore.LoraCoreMod;
import com.loracore.computer.IMemoryMappedDevice;
import com.loracore.computer.RawDiskDrive;
import com.loracore.computer.SystemBus;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * MMIO Контроллер жесткого диска с поддержкой DMA.
 * Позволяет читать/писать сектора по 512 байт.
 * Операции выполняются асинхронно, не блокируя основной поток сервера.
 *
 * Регистры (смещения):
 * 0x00: STATUS (R) / COMMAND (W)
 *       Read: 0=Ready, 1=Busy, 2=Error
 *       Write: 1=Read Sector, 2=Write Sector
 * 0x04: ERROR_CODE (R)
 * 0x08: LBA (RW) - Номер сектора
 * 0x0C: SECTOR_COUNT (RW) - Количество секторов
 * 0x10: DMA_ADDR (RW) - Адрес в RAM
 */
public class DiskControllerDevice implements IMemoryMappedDevice {

    private static final int SECTOR_SIZE = 512;

    private final RawDiskDrive drive; // Используем RawDiskDrive вместо IFileSystem
    private final SystemBus bus;      // Шина для DMA доступа к RAM

    // Регистры (volatile, так как к ним обращаются из разных потоков)
    private volatile int status = 0;
    private volatile int errorCode = 0;
    private volatile int lba = 0;
    private volatile int sectorCount = 1;
    private volatile int dmaAddress = 0;

    public DiskControllerDevice(SystemBus bus, RawDiskDrive drive) {
        this.bus = bus;
        this.drive = drive;
    }

    @Override
    public int getSize() {
        return 64; // Окно регистров
    }

    @Override
    public byte read(int offset) {
        int val = readInt(offset & ~3);
        return (byte) (val >> ((offset % 4) * 8));
    }

    @Override
    public int readInt(int offset) {
        return switch (offset) {
            case 0x00 -> status;
            case 0x04 -> errorCode;
            case 0x08 -> lba;
            case 0x0C -> sectorCount;
            case 0x10 -> dmaAddress;
            default -> 0;
        };
    }

    @Override
    public void write(int offset, byte value) {
        // Поддерживаем только 32-битную запись для простоты
    }

    @Override
    public void writeInt(int offset, int value) {
        switch (offset) {
            case 0x00: // COMMAND Register
                executeCommand(value);
                break;
            case 0x08: lba = value; break;
            case 0x0C: sectorCount = value; break;
            case 0x10: dmaAddress = value; break;
        }
    }

    private void executeCommand(int cmd) {
        if (status == 1) return; // Уже занят (Busy)

        // Захватываем параметры для использования в асинхронном потоке
        final int capturedLba = lba;
        final int capturedCount = sectorCount;
        final int capturedDma = dmaAddress;

        // Устанавливаем статус BUSY немедленно
        status = 1;
        errorCode = 0;

        // Запускаем операцию ввода-вывода асинхронно
        CompletableFuture.runAsync(() -> {
            try {
                switch (cmd) {
                    case 1: // READ SECTOR(S)
                        performRead(capturedLba, capturedCount, capturedDma);
                        break;
                    case 2: // WRITE SECTOR(S)
                        performWrite(capturedLba, capturedCount, capturedDma);
                        break;
                    default:
                        errorCode = 1; // Unknown Command
                        break;
                }
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("Disk I/O Error (Async)", e);
                errorCode = 2; // IO Error
            } finally {
                // Сбрасываем статус в READY после завершения
                status = 0;
            }
        });
    }

    private void performRead(int startLba, int count, int startDmaAddr) throws IOException {
        byte[] sectorBuffer = new byte[SECTOR_SIZE];

        for (int i = 0; i < count; i++) {
            int currentLba = startLba + i;
            int memAddr = startDmaAddr + (i * SECTOR_SIZE);

            // 1. Читаем с диска (медленно, блокирующе, но в фоновом потоке)
            drive.readSector(currentLba, sectorBuffer, 0);

            // 2. Пишем в RAM через DMA (быстро)
            // Внимание: прямой доступ к памяти из другого потока.
            // GenericRam должен быть thread-safe или массив байт в Java достаточно безопасен для побайтовой записи.
            for (int b = 0; b < SECTOR_SIZE; b++) {
                bus.writeByte(memAddr + b, sectorBuffer[b]);
            }
        }
        // Логируем только если операции редкие, иначе заспамим лог
        // LoraCoreMod.LOGGER.debug("[Disk] Async Read LBA {} count {} done.", startLba, count);
    }

    private void performWrite(int startLba, int count, int startDmaAddr) throws IOException {
        byte[] sectorBuffer = new byte[SECTOR_SIZE];

        for (int i = 0; i < count; i++) {
            int currentLba = startLba + i;
            int memAddr = startDmaAddr + (i * SECTOR_SIZE);

            // 1. Читаем из RAM через DMA (быстро)
            for (int b = 0; b < SECTOR_SIZE; b++) {
                sectorBuffer[b] = bus.readByte(memAddr + b);
            }

            // 2. Пишем на диск (медленно, блокирующе, но в фоновом потоке)
            drive.writeSector(currentLba, sectorBuffer, 0);
        }
        // LoraCoreMod.LOGGER.debug("[Disk] Async Write LBA {} count {} done.", startLba, count);
    }
}