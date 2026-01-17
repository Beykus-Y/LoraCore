package lora.emulator.bus;

import java.util.ArrayList;
import java.util.List;

public class SystemBus {
    // 16MB адресного пространства - 256 сегментов по 64KB
    private static final int SEGMENT_SHIFT = 16; // 64KB = 2^16
    private static final int SEGMENT_COUNT = 256;

    /**
     * Один сегмент может содержать несколько устройств
     */
    private final List<MappedDevice>[] segments = new ArrayList[SEGMENT_COUNT];

    public SystemBus() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = new ArrayList<>();
        }
    }

    /**
     * Описывает конкретный диапазон устройства в сегменте
     */
    private static final class MappedDevice {
        final IMemoryMappedDevice device;
        final int baseAddress;
        final int endAddress; // inclusive

        MappedDevice(IMemoryMappedDevice device, int baseAddress) {
            this.device = device;
            this.baseAddress = baseAddress;
            this.endAddress = baseAddress + device.getSize() - 1;
        }

        boolean contains(int address) {
            // Используем Integer.compareUnsigned для корректной работы с адресами > 2GB (если вдруг)
            // Но для скорости оставим простые сравнения, так как SystemBus отсечет лишнее
            return address >= baseAddress && address <= endAddress;
        }
    }

    public void mapDevice(int startAddress, IMemoryMappedDevice device) {
        int size = device.getSize();
        int endAddress = startAddress + size - 1;

        // Проверка выхода за адресное пространство
        if (startAddress < 0 || endAddress >= (SEGMENT_COUNT << SEGMENT_SHIFT)) {
            // Разрешаем маппить в верхнюю память (MMIO), если она попадает в сегменты
            // Но лучше просто проверить по сегментам
        }

        // [KOWALSKI FIX]: Используем >>> для беззнакового сдвига при расчете сегментов
        int segStart = startAddress >>> SEGMENT_SHIFT;
        int segEnd = endAddress >>> SEGMENT_SHIFT;

        if (segEnd >= SEGMENT_COUNT) {
            throw new IllegalArgumentException(String.format(
                    "Device [%s] mapped outside address space: 0x%04X-0x%04X",
                    device.getClass().getSimpleName(), startAddress, endAddress));
        }

        // Проверка пересечений по диапазонам
        for (int seg = segStart; seg <= segEnd; seg++) {
            for (MappedDevice md : segments[seg]) {
                boolean overlap =
                        startAddress <= md.endAddress &&
                                endAddress >= md.baseAddress;

                if (overlap) {
                    throw new IllegalStateException(String.format(
                            "[BUS] FATAL: Address conflict at segment %d (0x%04X). "
                                    + "Device [%s] overlaps [%s]",
                            seg, seg << SEGMENT_SHIFT,
                            device.getClass().getSimpleName(),
                            md.device.getClass().getSimpleName()));
                }
            }
        }

        // Маппинг
        MappedDevice mapped = new MappedDevice(device, startAddress);
        for (int seg = segStart; seg <= segEnd; seg++) {
            segments[seg].add(mapped);
        }

        System.out.printf("[BUS] Device [%s] mapped to 0x%04X-0x%04X (%d bytes)%n",
                device.getClass().getSimpleName(),
                startAddress,
                endAddress,
                size);
    }

    public byte readByte(int address) {
        // [KOWALSKI FIX]: >>> вместо >>
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return 0;

        // Тут оптимизировать нечего, байт есть байт
        for (MappedDevice md : segments[seg]) {
            if (md.contains(address)) {
                return md.device.read(address - md.baseAddress);
            }
        }
        return 0;
    }

    public void writeByte(int address, byte value) {
        // [KOWALSKI FIX]: >>> вместо >>
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return;

        for (MappedDevice md : segments[seg]) {
            if (md.contains(address)) {
                md.device.write(address - md.baseAddress, value);
                return;
            }
        }
    }

    // Оптимизированное чтение int
    public int readInt(int address) {
        // [KOWALSKI FIX]: >>> вместо >>. Это предотвратит IndexOutOfBounds при отрицательных адресах.
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return 0;

        for (MappedDevice md : segments[seg]) {
            if (!md.contains(address)) continue;

            int offset = address - md.baseAddress;
            int size = md.device.getSize();

            // Если int пересекает границу устройства - читаем побайтово (fallback)
            if (offset < 0 || offset + 3 >= size) {
                return ((readByte(address) & 0xFF) |
                        ((readByte(address + 1) & 0xFF) << 8) |
                        ((readByte(address + 2) & 0xFF) << 16) |
                        ((readByte(address + 3) & 0xFF) << 24));
            }

            // Иначе вызываем readInt самого устройства.
            return md.device.readInt(offset);
        }
        return 0;
    }

    public void writeInt(int address, int value) {
        // [KOWALSKI FIX]: >>> вместо >>
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return;

        for (MappedDevice md : segments[seg]) {
            if (!md.contains(address)) continue;

            int offset = address - md.baseAddress;
            int size = md.device.getSize();

            // Граничные условия
            if (offset < 0 || offset + 3 >= size) {
                writeByte(address, (byte) (value & 0xFF));
                writeByte(address + 1, (byte) ((value >> 8) & 0xFF));
                writeByte(address + 2, (byte) ((value >> 16) & 0xFF));
                writeByte(address + 3, (byte) ((value >> 24) & 0xFF));
                return;
            }

            md.device.writeInt(offset, value);
            return;
        }
    }
}