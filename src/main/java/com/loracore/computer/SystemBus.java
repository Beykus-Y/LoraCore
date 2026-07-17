package com.loracore.computer;

import com.loracore.LoraCoreMod;

/**
 * Оптимизированная системная шина с O(1) доступом через сегментную адресацию.
 * Использует массив IMemoryMappedDevice[256] вместо ArrayList для максимальной производительности.
 * 
 * Адресное пространство: 16MB (256 сегментов по 64KB).
 * 
 * Zero-allocation: все операции работают без создания объектов в горячем цикле.
 */
public class SystemBus {
    // 16MB адресного пространства - 256 сегментов по 64KB
    private static final int SEGMENT_SHIFT = 16; // 64KB = 2^16
    private static final int SEGMENT_COUNT = 256;
    private static final int MAX_ADDRESS = (SEGMENT_COUNT << SEGMENT_SHIFT) - 1; // 0xFFFFFF

    /**
     * Один сегмент может содержать несколько устройств.
     * Используем массив вместо ArrayList для O(1) доступа.
     */
    private final IMemoryMappedDevice[][] segments = new IMemoryMappedDevice[SEGMENT_COUNT][];
    private final int[] segmentSizes = new int[SEGMENT_COUNT]; // Количество устройств в сегменте
    private IInterruptHandler interruptHandler;
    private final IPortDevice[] ports = new IPortDevice[65536];
    private final java.util.concurrent.atomic.AtomicInteger pendingInterrupts = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Описывает конкретный диапазон устройства в сегменте.
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
            return address >= baseAddress && address <= endAddress;
        }
    }

    // Внутреннее хранилище устройств (для проверки пересечений)
    private final MappedDevice[] allDevices = new MappedDevice[64]; // Максимум 64 устройства
    private int deviceCount = 0;

    public SystemBus() {
        // Инициализируем массивы сегментов
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = new IMemoryMappedDevice[4]; // Начальный размер 4 устройства на сегмент
            segmentSizes[i] = 0;
        }
    }

    /**
     * Маппит устройство в адресное пространство.
     * @param startAddress Начальный адрес (24-битный, 0x000000 - 0xFFFFFF)
     * @param device Устройство для маппинга
     * @throws IllegalArgumentException если адрес выходит за пределы или есть конфликт
     */
    public void mapDevice(int startAddress, IMemoryMappedDevice device) {
        // Проверка выхода за адресное пространство
        if (startAddress < 0 || startAddress > MAX_ADDRESS) {
            throw new IllegalArgumentException(String.format(
                    "Device [%s] mapped outside address space: 0x%06X",
                    device.getClass().getSimpleName(), startAddress));
        }

        int size = device.getSize();
        int endAddress = startAddress + size - 1;

        if (endAddress > MAX_ADDRESS) {
            throw new IllegalArgumentException(String.format(
                    "Device [%s] extends beyond address space: 0x%06X-0x%06X",
                    device.getClass().getSimpleName(), startAddress, endAddress));
        }

        // Используем >>> для беззнакового сдвига
        int segStart = startAddress >>> SEGMENT_SHIFT;
        int segEnd = endAddress >>> SEGMENT_SHIFT;

        if (segEnd >= SEGMENT_COUNT) {
            throw new IllegalArgumentException(String.format(
                    "Device [%s] mapped outside address space: 0x%06X-0x%06X",
                    device.getClass().getSimpleName(), startAddress, endAddress));
        }

        // Проверка пересечений по диапазонам
        for (int i = 0; i < deviceCount; i++) {
            MappedDevice md = allDevices[i];
            boolean overlap =
                    startAddress <= md.endAddress &&
                            endAddress >= md.baseAddress;

            if (overlap) {
                throw new IllegalStateException(String.format(
                        "[BUS] FATAL: Address conflict. Device [%s] overlaps [%s] at 0x%06X-0x%06X",
                        device.getClass().getSimpleName(),
                        md.device.getClass().getSimpleName(),
                        startAddress, endAddress));
            }
        }

        // Маппинг: добавляем устройство во все затронутые сегменты
        MappedDevice mapped = new MappedDevice(device, startAddress);
        allDevices[deviceCount++] = mapped;

        for (int seg = segStart; seg <= segEnd; seg++) {
            int currentSize = segmentSizes[seg];
            if (currentSize >= segments[seg].length) {
                // Расширяем массив (редко, только при инициализации)
                IMemoryMappedDevice[] newArray = new IMemoryMappedDevice[currentSize * 2];
                System.arraycopy(segments[seg], 0, newArray, 0, currentSize);
                segments[seg] = newArray;
            }
            segments[seg][currentSize] = device;
            segmentSizes[seg]++;
        }

        LoraCoreMod.LOGGER.debug("[BUS] Device [{}] mapped to 0x{:06X}-0x{:06X} ({} bytes)",
                device.getClass().getSimpleName(), 
                String.format("%06X", startAddress),
                String.format("%06X", endAddress),
                size);
    }

    /**
     * Чтение байта по адресу.
     * Zero-allocation: не создает объектов.
     */
    public byte readByte(int address) {
        // Проверка границ адресного пространства
        if (address < 0 || address > MAX_ADDRESS) {
            throw new HardwareInterruptException(String.format(
                    "Memory access violation: address 0x%06X out of bounds", address));
        }

        // Используем >>> для беззнакового сдвига
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) {
            return 0;
        }

        // Ищем устройство в сегменте
        IMemoryMappedDevice[] segDevices = segments[seg];
        int segSize = segmentSizes[seg];
        
        for (int i = 0; i < segSize; i++) {
            IMemoryMappedDevice device = segDevices[i];
            // Проверяем, попадает ли адрес в диапазон устройства
            // Для этого нужно найти MappedDevice в allDevices
            for (int j = 0; j < deviceCount; j++) {
                MappedDevice md = allDevices[j];
                if (md.device == device && md.contains(address)) {
                    return device.read(address - md.baseAddress);
                }
            }
        }
        return 0; // Нет устройства по этому адресу
    }

    /**
     * Запись байта по адресу.
     * Zero-allocation: не создает объектов.
     */
    public void writeByte(int address, byte value) {
        // Проверка границ адресного пространства
        if (address < 0 || address > MAX_ADDRESS) {
            throw new HardwareInterruptException(String.format(
                    "Memory access violation: address 0x%06X out of bounds", address));
        }

        // Используем >>> для беззнакового сдвига
        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) {
            return;
        }

        // Ищем устройство в сегменте
        IMemoryMappedDevice[] segDevices = segments[seg];
        int segSize = segmentSizes[seg];
        
        for (int i = 0; i < segSize; i++) {
            IMemoryMappedDevice device = segDevices[i];
            // Проверяем, попадает ли адрес в диапазон устройства
            for (int j = 0; j < deviceCount; j++) {
                MappedDevice md = allDevices[j];
                if (md.device == device && md.contains(address)) {
                    device.write(address - md.baseAddress, value);
                    return;
                }
            }
        }
    }

    /**
     * Оптимизированное чтение int (little-endian).
     */
    public int readInt(int address) {
        if (address < 0 || address > MAX_ADDRESS - 3) {
            throw new HardwareInterruptException(String.format(
                    "Memory access violation: address 0x%06X out of bounds", address));
        }

        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return 0;

        IMemoryMappedDevice[] segDevices = segments[seg];
        int segSize = segmentSizes[seg];

        for (int i = 0; i < segSize; i++) {
            IMemoryMappedDevice device = segDevices[i];
            for (int j = 0; j < deviceCount; j++) {
                MappedDevice md = allDevices[j];
                if (md.device == device && md.contains(address)) {
                    int offset = address - md.baseAddress;
                    int size = md.device.getSize();

                    // Если пересекает границу - читаем побайтово LITTLE ENDIAN
                    if (offset < 0 || offset + 3 >= size) {
                        return (readByte(address) & 0xFF) |
                                ((readByte(address + 1) & 0xFF) << 8) |
                                ((readByte(address + 2) & 0xFF) << 16) |
                                ((readByte(address + 3) & 0xFF) << 24);
                    }

                    return device.readInt(offset);
                }
            }
        }
        return 0;
    }

    /**
     * Оптимизированная запись int (little-endian).
     */
    public void writeInt(int address, int value) {
        if (address < 0 || address > MAX_ADDRESS - 3) {
            throw new HardwareInterruptException(String.format(
                    "Memory access violation: address 0x%06X out of bounds", address));
        }

        int seg = address >>> SEGMENT_SHIFT;
        if (seg >= SEGMENT_COUNT) return;

        IMemoryMappedDevice[] segDevices = segments[seg];
        int segSize = segmentSizes[seg];

        for (int i = 0; i < segSize; i++) {
            IMemoryMappedDevice device = segDevices[i];
            for (int j = 0; j < deviceCount; j++) {
                MappedDevice md = allDevices[j];
                if (md.device == device && md.contains(address)) {
                    int offset = address - md.baseAddress;
                    int size = md.device.getSize();

                    // Если пересекает границу - пишем побайтово LITTLE ENDIAN
                    if (offset < 0 || offset + 3 >= size) {
                        writeByte(address,     (byte) (value & 0xFF));
                        writeByte(address + 1, (byte) ((value >>> 8) & 0xFF));
                        writeByte(address + 2, (byte) ((value >>> 16) & 0xFF));
                        writeByte(address + 3, (byte) ((value >>> 24) & 0xFF));
                        return;
                    }

                    device.writeInt(offset, value);
                    return;
                }
            }
        }
    }

    /**
     * Вызывает tick() у всех устройств, реализующих ITickable.
     */
    public void tickDevices(long cycles) {
        for (int i = 0; i < deviceCount; i++) {
            MappedDevice md = allDevices[i];
            if (md.device instanceof ITickable tickable) {
                tickable.tick(cycles);
            }
        }
    }
    /**
     * Устанавливает обработчик программных прерываний (Syscalls).
     */
    public void setInterruptHandler(IInterruptHandler handler) {
        this.interruptHandler = handler;
    }

    /**
     * Выполняет системный вызов. Вызывается из CPU при инструкции INT.
     */
    public void handleInterrupt(int code, int[] registers) {
        if (interruptHandler != null) {
            interruptHandler.handle(code, registers);
        } else {
            LoraCoreMod.LOGGER.warn("[BUS] Interrupt 0x{} called but no handler registered!",
                    Integer.toHexString(code));
        }
    }

    /**
     * Запись в порт (инструкция OUT).
     */
    public void writePort(int port, int value) {
        if (port >= 0 && port < ports.length && ports[port] != null) {
            ports[port].write(value);
        }
    }

    /**
     * Чтение из порта (инструкция IN).
     */
    public int readPort(int port) {
        if (port >= 0 && port < ports.length && ports[port] != null) {
            return ports[port].read();
        }
        return 0;
    }

    /**
     * Запрашивает прерывание. Вызывается устройствами (Клавиатура, Таймер).
     * @param irq Номер прерывания (0-15).
     */
    public void requestInterrupt(int irq) {
        validateIrq(irq);
        pendingInterrupts.getAndUpdate(current -> current | (1 << irq));
    }

    /**
     * Проверяет наличие активных прерываний.
     * @return Номер самого приоритетного прерывания или -1, если их нет.
     */
    public int checkPendingInterrupts() {
        int pending = pendingInterrupts.get();
        return pending == 0 ? -1 : Integer.numberOfTrailingZeros(pending);
    }

    /**
     * Сбрасывает флаг прерывания (вызывается CPU при начале обработки).
     */
    public void clearInterrupt(int irq) {
        validateIrq(irq);
        pendingInterrupts.getAndUpdate(current -> current & ~(1 << irq));
    }

    public void clearPendingInterrupts() {
        pendingInterrupts.set(0);
    }

    private static void validateIrq(int irq) {
        if (irq < 0 || irq >= 16) {
            throw new IllegalArgumentException("IRQ must be in range 0-15: " + irq);
        }
    }
}
