package lora.emulator.bus;

import java.util.Arrays;

public class GpuDevice implements IMemoryMappedDevice, ITickable {

    private final GpuSpecs.Config spec;
    public final int[] vram; // Прямой доступ для DebugWindow (Read-only для окна)

    // Смещение регистров относительно начала устройства
    private final int registersOffset;

    // --- КАРТА РЕГИСТРОВ (MMIO) ---
    // Смещения в байтах от начала зоны регистров
    private static final int REG_STATUS = 0x00; // [0]: 0=READY, 1=BUSY
    private static final int REG_CMD    = 0x04; // Command ID
    private static final int REG_ARG_0  = 0x08; // X / SrcAddr
    private static final int REG_ARG_1  = 0x0C; // Y / DestAddr
    private static final int REG_ARG_2  = 0x10; // W
    private static final int REG_ARG_3  = 0x14; // H
    private static final int REG_ARG_4  = 0x18; // Color
    private static final int REG_WIDTH  = 0x20;
    private static final int REG_HEIGHT = 0x24;

    // Команды
    private static final int CMD_CLEAR     = 1;
    private static final int CMD_FILL_RECT = 2;
    private static final int CMD_COPY_RECT = 3; // Только Tier 3

    // Внутреннее состояние GPU
    private int status = 0;       // 0 = Idle
    private int currentCmd = 0;
    private int rX, rY, rW, rH, rColor, rSrc, rDest; // Теневые регистры для исполнения
    private int cyclesRemaining = 0; // Сколько тактов осталось до завершения операции
    public int getWidth() {
        return spec.width;
    }

    public int getHeight() {
        return spec.height;
    }

    public GpuSpecs.Config getSpec() {
        return spec;
    }

    // Регистры MMIO (сырые данные)
    private final int[] mmioRegisters = new int[16];

    public GpuDevice(GpuSpecs.Config spec) {
        this.spec = spec;
        this.vram = new int[spec.vramSize];
        this.registersOffset = spec.vramSize * 4; // VRAM в байтах
    }

    public void writeInt(int offset, int value) {
        if (offset < registersOffset) {
            int idx = offset >> 2;
            if (idx >= 0 && idx < vram.length) {
                vram[idx] = value;
            }
            return;
        }
        // Запись в регистры MMIO
        int regIdx = (offset - registersOffset) >> 2;
        if (regIdx >= 0 && regIdx < mmioRegisters.length) {
            mmioRegisters[regIdx] = value;
            // Триггер команды при записи в REG_CMD
            if ((offset - registersOffset) == REG_CMD) {
                tryExecuteCommand();
            }
        }
    }

    @Override
    public int getSize() {
        // VRAM + 64 байта на регистры
        return registersOffset + 64;
    }

    @Override
    public byte read(int offset) {
        // Чтение из VRAM (медленно, но можно)
        if (offset < registersOffset) {
            int idx = offset / 4;
            int shift = (offset % 4) * 8;
            return (byte) (vram[idx] >> shift);
        }

        // Чтение регистров
        int regIdx = (offset - registersOffset) / 4;
        if (regIdx < mmioRegisters.length) {
            int shift = (offset % 4) * 8;
            // Особая логика: REG_STATUS читается динамически
            if ((offset - registersOffset) == REG_STATUS) {
                return (byte) (status >> shift);
            }
            return (byte) (mmioRegisters[regIdx] >> shift);
        }
        return 0;
    }

    @Override
    public void write(int offset, byte value) {
        // 1. Запись в VRAM (Пиксели)
        if (offset < registersOffset) {
            int idx = offset / 4;
            int shift = (offset % 4) * 8;
            int mask = ~(0xFF << shift);
            vram[idx] = (vram[idx] & mask) | ((value & 0xFF) << shift);
            return;
        }

        // 2. Запись в Регистры
        int localOffset = offset - registersOffset;
        int regIdx = localOffset / 4;

        if (regIdx >= mmioRegisters.length) return;

        // Накапливаем байты в int регистре
        int shift = (offset % 4) * 8;
        int mask = ~(0xFF << shift);
        mmioRegisters[regIdx] = (mmioRegisters[regIdx] & mask) | ((value & 0xFF) << shift);

        if (localOffset >= REG_CMD && localOffset < REG_CMD + 4 && (offset % 4 == 3)) {
            tryExecuteCommand();
        }
    }

    private void tryExecuteCommand() {
        if (status != 0) {
            System.err.println("[GPU] WARN: Command dropped, GPU Busy!");
            return;
        }
        if (!spec.hasAcceleration) {
            return; // Tier 1 игнорирует команды
        }

        currentCmd = mmioRegisters[REG_CMD / 4];

        // Копируем аргументы из MMIO во внутренние переменные
        rX = mmioRegisters[REG_ARG_0 / 4];
        rY = mmioRegisters[REG_ARG_1 / 4];
        rW = mmioRegisters[REG_ARG_2 / 4];
        rH = mmioRegisters[REG_ARG_3 / 4];
        rColor = mmioRegisters[REG_ARG_4 / 4];

        // Расчет стоимости операции (Latency)
        int pixels = 0;
        switch (currentCmd) {
            case CMD_CLEAR:
                pixels = spec.width * spec.height;
                break;
            case CMD_FILL_RECT:
                if (rW <= 0 || rH <= 0) return;
                pixels = rW * rH;
                break;
            case CMD_COPY_RECT:
                if (!spec.hasBlitter) return;
                pixels = rW * rH;
                break;
            default:
                return; // Неизвестная команда
        }

        // Вычисляем, сколько тиков займет операция
        if (pixels > 0) {
            cyclesRemaining = pixels / spec.pixelsPerTick;
            if (cyclesRemaining < 1) cyclesRemaining = 1; // Минимум 1 такт

            status = 1; // BUSY
            mmioRegisters[0] = 1; // [KOWALSKI FIX]: Sync register array immediately!
        }
        System.out.printf("[GPU] Executing CMD %d (X:%d Y:%d W:%d H:%d Color:%X)\n",
                currentCmd, rX, rY, rW, rH, rColor);
    }

    @Override
    public void tick(long cycles) {
        if (status == 0) return;

        cyclesRemaining -= cycles;

        if (cyclesRemaining <= 0) {
            // Работа завершена
            finishCommand();
            status = 0; // READY
            mmioRegisters[0] = 0; // Sync register array

            cyclesRemaining = 0;
        }
    }

    private void finishCommand() {
        switch (currentCmd) {
            case CMD_CLEAR:
                Arrays.fill(vram, rColor);
                break;

            case CMD_FILL_RECT:
                fillInfoVram(rX, rY, rW, rH, rColor);
                break;

            case CMD_COPY_RECT:
                blit(rX, rY, rW, rH, rColor);
                break;
        }
    }

    private void fillInfoVram(int x, int y, int w, int h, int color) {
        int bufferW = spec.width;
        int bufferH = spec.height;

        int startX = Math.max(0, x);
        int startY = Math.max(0, y);
        int endX = Math.min(bufferW, x + w);
        int endY = Math.min(bufferH, y + h);

        if (startX >= endX || startY >= endY) return;

        for (int cy = startY; cy < endY; cy++) {
            int offset = cy * bufferW + startX;
            int length = endX - startX;
            Arrays.fill(vram, offset, offset + length, color);
        }
    }

    private void blit(int sx, int sy, int w, int h, int destPacked) {
        // Implementation left as exercise
    }

    @Override
    public int readInt(int offset) {
        // 1. Чтение из VRAM
        if (offset < registersOffset) {
            int idx = offset >> 2;
            return vram[idx];
        }

        // 2. Чтение регистров MMIO
        int localOffset = offset - registersOffset;

        // --- НОВОЕ: Возвращаем параметры видеокарты ---
        if (localOffset == REG_WIDTH) return spec.width;
        if (localOffset == REG_HEIGHT) return spec.height;

        // [KOWALSKI FIX]: Force read from 'status' variable for REG_STATUS
        if (localOffset == REG_STATUS) return status;

        int regIdx = localOffset >> 2;
        return (regIdx >= 0 && regIdx < mmioRegisters.length) ? mmioRegisters[regIdx] : 0;
    }
}