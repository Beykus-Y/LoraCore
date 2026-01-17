import lora.emulator.bus.DiskDevice;
import lora.emulator.bus.SystemBus;
import lora.emulator.memory.RamStick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DiskDeviceTest {

    private SystemBus bus;
    private DiskDevice disk;
    private File tempFile;
    private RamStick ram;

    // Адреса регистров (должны совпадать с реализацией DiskDevice)
    private static final int REG_STATUS = 0;
    private static final int REG_CMD    = 4; // Смещение в байтах!
    private static final int REG_LBA    = 8;
    private static final int REG_ADDR   = 12;

    @BeforeEach
    void setup() throws IOException {
        bus = new SystemBus();
        // Создаем временный файл диска
        tempFile = File.createTempFile("lora_test_disk", ".img");
        tempFile.deleteOnExit();

        disk = new DiskDevice(bus, tempFile);
        ram = new RamStick(4096, "RAM");

        bus.mapDevice(0x80000, disk); // Диск далеко
        bus.mapDevice(0x0000, ram);   // ОЗУ в начале
    }

    @AfterEach
    void tearDown() throws IOException {

        if (tempFile != null) tempFile.delete();
    }

    @Test
    void testWriteAndReadSector() {
        int diskBase = 0x80000;

        // 1. Подготовим данные в RAM (0x0000)
        // Пишем "Dead Beef" паттерн
        bus.writeInt(0x0000, 0xDEADBEEF);
        bus.writeInt(0x0004, 0xCAFEBABE);

        // 2. Настраиваем диск на ЗАПИСЬ (WRITE) из RAM 0x0000 в Сектор 0
        bus.writeInt(diskBase + REG_LBA, 0);      // Sector 0
        bus.writeInt(diskBase + REG_ADDR, 0x0000);// RAM Address
        bus.writeInt(diskBase + REG_CMD, 2);      // CMD 2 = WRITE

        // 3. Тикаем диском, пока он занят
        // В реализации я ставил 1000 тактов задержки
        for (int i = 0; i < 1100; i++) {
            disk.tick(1);
        }

        assertEquals(0, bus.readInt(diskBase + REG_STATUS), "Disk should be READY after tick");

        // 4. Очищаем RAM, чтобы проверить чтение
        bus.writeInt(0x0000, 0);
        bus.writeInt(0x0004, 0);

        // 5. Читаем обратно (READ) из Сектора 0 в RAM 0x0000
        bus.writeInt(diskBase + REG_CMD, 1); // CMD 1 = READ

        for (int i = 0; i < 1100; i++) {
            disk.tick(1);
        }

        // 6. Проверяем
        assertEquals(0xDEADBEEF, bus.readInt(0x0000), "Data mismatch after Disk Read/Write loop");
        assertEquals(0xCAFEBABE, bus.readInt(0x0004));
    }
}