import lora.emulator.bus.SystemBus;
import lora.emulator.memory.RamStick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemBusTest {

    private SystemBus bus;

    @BeforeEach
    void setup() {
        bus = new SystemBus();
    }

    @Test
    @DisplayName("Mapping Logic: Overlap Detection")
    void testMappingOverlap() {
        bus.mapDevice(0x0000, new RamStick(1024, "Base RAM"));

        // Попытка воткнуть устройство, которое наезжает на существующее
        // 0x0000 - 0x03FF занято.
        // Пытаемся мапить в 0x0200
        Exception ex = assertThrows(IllegalStateException.class, () -> {
            bus.mapDevice(0x0200, new RamStick(512, "Conflict RAM"));
        });

        assertTrue(ex.getMessage().contains("Address conflict"), "Bus must shout about conflicts");
    }

    @Test
    @DisplayName("Mapping Logic: Out of Bounds")
    void testMappingOutOfBounds() {
        // У тебя лимит 16MB (256 сегментов по 64KB)
        // 16MB = 0x1000000. 

        assertThrows(IllegalArgumentException.class, () -> {
            bus.mapDevice(0x1000000, new RamStick(256, "Void RAM"));
        }, "Should not map outside address space");
    }

    @Test
    @DisplayName("R/W: Cross-Boundary Int Read")
    void testCrossDeviceRead() {
        // ЭТО ВАЖНЫЙ ТЕСТ.
        // Что будет, если int лежит на границе двух устройств или устройства и пустоты?
        // Твоя реализация в SystemBus.readInt делает fallback на побайтовое чтение. Проверим.

        RamStick stick1 = new RamStick(4, "Tiny"); // 0x00 - 0x03
        bus.mapDevice(0x0000, stick1);

        bus.writeByte(0x0002, (byte)0xAA);
        bus.writeByte(0x0003, (byte)0xBB);
        // 0x0004 - пусто, вернет 0
        // 0x0005 - пусто, вернет 0

        // Читаем int с адреса 0x0002. Ожидаем: [00][00][BB][AA] -> 0x0000BBAA
        int val = bus.readInt(0x0002);

        assertEquals(0x0000BBAA, val, "Bus failed to handle cross-boundary read");
    }

    @Test
    @DisplayName("MMIO: GPU Register Access")
    void testGpuRegisters() {
        // Проверим, что SystemBus корректно пробрасывает readInt/writeInt в устройство
        // Для этого нужен мок или реальный GPUDevice. Возьмем реальный, раз он есть.

        lora.emulator.bus.GpuSpecs.Config spec = lora.emulator.bus.GpuSpecs.TIER_1_BASIC;
        lora.emulator.bus.GpuDevice gpu = new lora.emulator.bus.GpuDevice(spec);

        // Маппим GPU куда-нибудь далеко
        int gpuBase = 0x4000;
        bus.mapDevice(gpuBase, gpu);

        // VRAM Size = 128*128 = 16384 (0x4000) байт.
        // Регистры начинаются с base + vramSize.
        int regStatusAddr = gpuBase + (128*128); // 0x4000 + 0x4000 = 0x8000

        // Чтение статуса (должен быть 0)
        assertEquals(0, bus.readInt(regStatusAddr));

        // Запись в VRAM через шину
        bus.writeInt(gpuBase, 0xFFFFFFFF);
        assertEquals(0xFFFFFFFF, bus.readInt(gpuBase));
    }
}