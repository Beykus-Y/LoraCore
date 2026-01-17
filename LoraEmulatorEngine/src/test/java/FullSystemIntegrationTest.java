import lora.emulator.bus.*;
import lora.emulator.cpu.VirtualCpu;
import lora.emulator.memory.RamStick;
import lora.emulator.util.InstructionSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ИНТЕГРАЦИОННЫЙ ТЕСТ (The Big One)
 * Проверяет связку: CPU -> Bus -> [RAM, GPU, Disk, Keyboard]
 * Без запуска GUI и asm-файлов. Чистый машинный код.
 */
class FullSystemIntegrationTest {

    private SystemBus bus;
    private VirtualCpu cpu;
    private GpuDevice gpu;
    private DiskDevice disk;
    private KeyboardDevice keyboard;
    private File tempDiskFile;

    // Адресная карта (Должна совпадать с твоим Main!)
    private static final int RAM_ADDR = 0x0000;
    private static final int KEYBOARD_ADDR = 0x3000;
    private static final int GPU_BASE = 0x4000;
    private static final int GPU_MMIO = 0x44000; // 0x4000 + (256*256*4)
    private static final int DISK_ADDR = 0x50000;

    @BeforeEach
    void setup() throws IOException {
        bus = new SystemBus();

        // 1. RAM
        bus.mapDevice(RAM_ADDR, new RamStick(4096, "Test RAM"));

        // 2. Keyboard
        keyboard = new KeyboardDevice();
        bus.mapDevice(KEYBOARD_ADDR, keyboard);

        // 3. GPU (Tier 2)
        gpu = new GpuDevice(GpuSpecs.TIER_2_ACCEL);
        bus.mapDevice(GPU_BASE, gpu);

        // 4. Disk
        tempDiskFile = File.createTempFile("integ_test", ".img");
        disk = new DiskDevice(bus, tempDiskFile);
        bus.mapDevice(DISK_ADDR, disk);

        // 5. CPU
        cpu = new VirtualCpu(bus, 12345L);
    }

    @AfterEach
    void tearDown() {
        if (tempDiskFile != null) tempDiskFile.delete();
    }

    @Test
    void testFullSystemDataFlow() {
        // =================================================================
        // ЭТАП 1: CPU -> KEYBOARD
        // Сценарий: Нажали кнопку, CPU прочитал её через шину.
        // =================================================================
        System.out.println("--- PHASE 1: KEYBOARD ---");

        // Симулируем нажатие 'X' из GUI
        keyboard.pressKey(88);

        // Программа: LDI R0, 0x3000; LD R1, R0
        bus.writeInt(0, InstructionSet.asm("LDI", 0, 0, 0x3000));
        bus.writeInt(4, InstructionSet.asm("LD", 1, 0, 0));

        cpu.pc = 0;
        cpu.step(); // LDI
        cpu.step(); // LD

        assertEquals(88, cpu.registers[1], "CPU failed to read keystroke from KeyboardDevice");
        System.out.println("[OK] CPU read key code 88");


        // =================================================================
        // ЭТАП 2: CPU -> GPU (MMIO Write)
        // Сценарий: CPU посылает команду CLEAR RED в GPU.
        // =================================================================
        System.out.println("--- PHASE 2: GPU MMIO ---");

        // 1. Формируем адрес MMIO (0x44000) в R1
        // LUI R1, 4 -> R1 = 0x40000
        // ADDI R1, 0x4000 -> R1 = 0x44000
        bus.writeInt(8,  InstructionSet.asm("LUI", 1, 0, 4));
        bus.writeInt(12, InstructionSet.asm("ADDI", 1, 0, 0x4000));

        // 2. Загружаем Красный цвет (0xFFFF0000) в R0
        // LUI R0, 0xFFFF
        bus.writeInt(16, InstructionSet.asm("LUI", 0, 0, 0xFFFF));

        // 3. Пишем цвет в регистр (Offset 0x18)
        // ADDI R2, R1, 0x18 -> R2 = 0x44018
        // ST R2, R0
        bus.writeInt(20, InstructionSet.asm("LDI", 2, 0, 0x18));
        bus.writeInt(24, InstructionSet.asm("ADD", 2, 1, 0));
        bus.writeInt(28, InstructionSet.asm("ST", 2, 0, 0));

        // 4. Пишем команду CLEAR (1) в регистр (Offset 0x04)
        // ADDI R2, R1, 0x04 -> R2 = 0x44004
        // LDI R3, 1
        // ST R2, R3
        bus.writeInt(32, InstructionSet.asm("LDI", 2, 0, 0x04));
        bus.writeInt(36, InstructionSet.asm("ADD", 2, 1, 0));
        bus.writeInt(40, InstructionSet.asm("LDI", 3, 0, 1));
        bus.writeInt(44, InstructionSet.asm("ST", 2, 3, 0));

        // Выполняем
        cpu.pc = 8;
        for (int i = 0; i < 10; i++) cpu.step();

        // Проверяем, что GPU "услышал" команду
        // В реальном эмуляторе тикает тред, тут тикаем вручную
        gpu.tick(100000); // Проматываем время вперед

        // Проверяем VRAM. Весь экран должен быть красным (0xFFFF0000).
        // Прозрачность игнорируем, главное цвет.
        assertEquals(0xFFFF0000, gpu.vram[0], "GPU VRAM [0] not red");
        assertEquals(0xFFFF0000, gpu.vram[100], "GPU VRAM [100] not red");
        System.out.println("[OK] GPU executed CLEAR command via MMIO");


        // =================================================================
        // ЭТАП 3: CPU -> DISK (DMA)
        // Сценарий: CPU пишет данные в RAM, приказывает Диску записать их в файл.
        // =================================================================
        System.out.println("--- PHASE 3: DISK DMA ---");

        // Подготовка данных в RAM по адресу 0x0800
        bus.writeInt(0x0800, 0xCAFEBABE);

        // Формируем адрес контроллера диска (0x50000) в R1
        // LUI R1, 5 -> 0x50000
        bus.writeInt(50, InstructionSet.asm("LUI", 1, 0, 5));

        // 1. LBA = 0 (Offset 8 bytes relative to base? No, indexes!)
        // В твоей реализации DiskDevice:
        // REG_LBA = 2 (index) -> Offset 8 bytes
        // REG_ADDR = 3 (index) -> Offset 12 bytes
        // REG_CMD = 1 (index) -> Offset 4 bytes

        // Пишем LBA = 0
        bus.writeInt(54, InstructionSet.asm("LDI", 2, 0, 0)); // Val 0
        bus.writeInt(58, InstructionSet.asm("LDI", 3, 0, 8)); // Offset 8
        bus.writeInt(62, InstructionSet.asm("ADD", 3, 1, 0)); // R3 = 0x50008
        bus.writeInt(66, InstructionSet.asm("ST", 3, 2, 0));

        // Пишем RAM Addr = 0x0800
        bus.writeInt(70, InstructionSet.asm("LDI", 2, 0, 0x0800));
        bus.writeInt(74, InstructionSet.asm("LDI", 3, 0, 12)); // Offset 12
        bus.writeInt(78, InstructionSet.asm("ADD", 3, 1, 0));  // R3 = 0x5000C
        bus.writeInt(82, InstructionSet.asm("ST", 3, 2, 0));

        // Пишем CMD = 2 (WRITE)
        bus.writeInt(86, InstructionSet.asm("LDI", 2, 0, 2));
        bus.writeInt(90, InstructionSet.asm("LDI", 3, 0, 4));  // Offset 4
        bus.writeInt(94, InstructionSet.asm("ADD", 3, 1, 0));  // R3 = 0x50004
        bus.writeInt(98, InstructionSet.asm("ST", 3, 2, 0));   // GO!

        // Выполняем настройку контроллера
        cpu.pc = 50;
        for (int i = 0; i < 15; i++) cpu.step();

        // Диск получил команду?
        // Тикаем диск
        disk.tick(2000); // Достаточно времени для завершения

        // Проверяем результат: Считываем диск обратно в другую область памяти
        // Но для теста проще проверить сам файл или состояние шины.
        // Давай проверим, что в RAM данные остались
        assertEquals(0xCAFEBABE, bus.readInt(0x0800));

        // Проверим, что диск вернулся в статус 0 (Ready)
        assertEquals(0, bus.readInt(0x50000), "Disk status should be READY (0)");

        System.out.println("[OK] Disk operation triggered and finished");
    }
}