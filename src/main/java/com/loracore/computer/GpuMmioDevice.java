package com.loracore.computer;

import com.loracore.LoraCoreMod;

import java.io.InputStream;
import java.util.Arrays;

/**
 * GPU устройство с MMIO, связанное с ServerScreenState через SystemBus.
 * VRAM находится в адресном пространстве 0x400000 - 0x47A120 (960x540 пикселей).
 * 
 * Реализует IMemoryMappedDevice и ITickable для работы с шиной.
 */
public class GpuMmioDevice implements IMemoryMappedDevice, ITickable {
    
    public enum GpuTier {
        TIER1,
        TIER2,
        TIER3
    }
    
    // Разрешение экрана (High DPI)
    public static final int SCREEN_WIDTH = 960;
    public static final int SCREEN_HEIGHT = 540;
    public static final int VRAM_SIZE = SCREEN_WIDTH * SCREEN_HEIGHT; // Количество пикселей (int)
    public static final int VRAM_SIZE_BYTES = VRAM_SIZE * 4; // RGBA по 4 байта на пиксель
    
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
    private static final int CMD_DRAW_CHAR = 3;
    private static final int CMD_SCROLL = 4;
    private static final int TOTAL_CHARS = 2048;


    private final byte[] fontData = new byte[TOTAL_CHARS * CHAR_HEIGHT];
    private static final int CHAR_HEIGHT = 16;
    
    // Внутреннее состояние GPU
    private int status = 0;       // 0 = Idle
    private int currentCmd = 0;
    private int rX, rY, rW, rH, rColor, rSrc, rDest; // Теневые регистры для исполнения
    private int cyclesRemaining = 0; // Сколько тактов осталось до завершения операции
    private int scanoutTimer = 0;
    private static final int SCANOUT_INTERVAL = 2000;
    // Регистры MMIO (сырые данные)
    private final int[] mmioRegisters = new int[16];
    
    // VRAM как массив int (RGBA пиксели)
    private final int[] vram;
    
    // Ссылка на ServerScreenState для синхронизации
    private final ServerScreenState screenState;
    private final GpuTier tier;
    private final int pixelsPerCycle;
    
    public GpuMmioDevice(ServerScreenState screenState) {
        this(screenState, GpuTier.TIER3);
    }
    
    public GpuMmioDevice(ServerScreenState screenState, GpuTier tier) {
        this.screenState = screenState;
        this.tier = tier;
        this.vram = new int[VRAM_SIZE];
        this.registersOffset = VRAM_SIZE_BYTES; // VRAM в байтах
        if (tier == GpuTier.TIER1) {
            this.pixelsPerCycle = 16;
        } else if (tier == GpuTier.TIER2) {
            this.pixelsPerCycle = 128;
        } else {
            this.pixelsPerCycle = VRAM_SIZE;
        }
        
        // Инициализируем VRAM черным цветом
        Arrays.fill(vram, 0xFF000000); // RGBA: черный с полной непрозрачностью
        loadFont();
        // Синхронизируем с ServerScreenState
        syncToScreenState();
    }

    private void loadFont() {
        try (InputStream is = getClass().getResourceAsStream("/assets/loracore/os/font.bin")) {
            if (is != null) {
                int read = is.readNBytes(fontData, 0, fontData.length);
                if (read != fontData.length) {
                    throw new java.io.EOFException("Expected " + fontData.length
                            + " font bytes, got " + read);
                }
                LoraCoreMod.LOGGER.info("GpuMmioDevice: Loaded font ({} bytes)", read);
            } else {
                LoraCoreMod.LOGGER.warn("GpuMmioDevice: font.bin resource not found");
            }
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("GpuMmioDevice: Failed to load font.bin", e);
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
            if (idx >= 0 && idx < vram.length) {
                return (byte) (vram[idx] >> shift);
            }
            return 0;
        }
        
        // Чтение регистров
        int localOffset = offset - registersOffset;
        int regIdx = localOffset / 4;
        if (regIdx < mmioRegisters.length) {
            int shift = (offset % 4) * 8;
            // Особая логика: REG_STATUS читается динамически
            if (localOffset == REG_STATUS) {
                return (byte) (status >> shift);
            }
            // REG_WIDTH и REG_HEIGHT возвращают константы
            if (localOffset == REG_WIDTH) {
                return (byte) ((SCREEN_WIDTH >> shift) & 0xFF);
            }
            if (localOffset == REG_HEIGHT) {
                return (byte) ((SCREEN_HEIGHT >> shift) & 0xFF);
            }
            return (byte) (mmioRegisters[regIdx] >> shift);
        }
        return 0;
    }
    
    @Override
    public void write(int offset, byte value) {
        if (offset < registersOffset) {
            int idx = offset / 4;
            int shift = (offset % 4) * 8;
            if (idx >= 0 && idx < vram.length) {
                int mask = ~(0xFF << shift);
                vram[idx] = (vram[idx] & mask) | ((value & 0xFF) << shift);
                vramDirty = true;
                // ВНИМАНИЕ: Здесь мы не вызываем syncToScreenState(), это слишком медленно.
                // Синхронизация произойдет в методе tick().
            }
            return;
        }

        int localOffset = offset - registersOffset;
        int regIdx = localOffset / 4;

        if (regIdx >= mmioRegisters.length) return;

        int shift = (offset % 4) * 8;
        int mask = ~(0xFF << shift);
        mmioRegisters[regIdx] = (mmioRegisters[regIdx] & mask) | ((value & 0xFF) << shift);

        if (localOffset >= REG_CMD && localOffset < REG_CMD + 4 && (offset % 4 == 3)) {
            tryExecuteCommand();
        }
    }
    
    @Override
    public int readInt(int offset) {
        // 1. Чтение из VRAM
        if (offset < registersOffset) {
            int idx = offset >> 2;
            if (idx >= 0 && idx < vram.length) {
                return vram[idx];
            }
            return 0;
        }
        
        // 2. Чтение регистров MMIO
        int localOffset = offset - registersOffset;
        
        // --- Возвращаем параметры видеокарты ---
        if (localOffset == REG_WIDTH) return 480;
        if (localOffset == REG_HEIGHT) return 270;
        
        // REG_STATUS читается динамически
        if (localOffset == REG_STATUS) return status;
        
        int regIdx = localOffset >> 2;
        return (regIdx >= 0 && regIdx < mmioRegisters.length) ? mmioRegisters[regIdx] : 0;
    }
    
    @Override
    public void writeInt(int offset, int value) {
        if (offset < registersOffset) {
            int idx = offset >> 2;
            if (idx >= 0 && idx < vram.length) {
                vram[idx] = value;
                vramDirty = true; // Помечаем, что были изменения
            }
            return;
        }

        int localOffset = offset - registersOffset;
        int regIdx = localOffset >> 2;

        if (regIdx >= 0 && regIdx < mmioRegisters.length) {
            mmioRegisters[regIdx] = value;
            if (localOffset == REG_CMD) {
                tryExecuteCommand();
            }
        }
    }


    private void tryExecuteCommand() {
        if (status != 0) {
            LoraCoreMod.LOGGER.warn("[GPU] WARN: Command dropped, GPU Busy!");
            return;
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
                pixels = SCREEN_WIDTH * SCREEN_HEIGHT;
                break;
            case CMD_FILL_RECT:
                if (rW <= 0 || rH <= 0) return;
                pixels = rW * rH;
                break;
            case CMD_DRAW_CHAR: // <--- Расчет стоимости для символа
                pixels = 8 * CHAR_HEIGHT; // 8x16 пикселей
                break;
            case CMD_SCROLL:
                // Операция затрагивает всё полотно экрана
                pixels = SCREEN_WIDTH * SCREEN_HEIGHT;
                break;
            default:
                return; // Неизвестная команда
        }
        
        if (pixels > 0) {
            cyclesRemaining = pixels / pixelsPerCycle;
            if (cyclesRemaining < 1) cyclesRemaining = 1; // Минимум 1 такт
            
            status = 1; // BUSY
            mmioRegisters[0] = 1; // Sync register array immediately
        }
        
        LoraCoreMod.LOGGER.debug("[GPU] Executing CMD {} (X:{} Y:{} W:{} H:{} Color:0x{})",
                currentCmd, rX, rY, rW, rH, String.format("%08X", rColor));
    }

    private void drawChar(int x, int y, int code, int color) {
        if (code < 0 || code >= 2048) return;
        int fontOffset = code * CHAR_HEIGHT;

        for (int row = 0; row < CHAR_HEIGHT; row++) {
            // Умножаем координату Y на 2
            int drawY = (y + row) * 2;
            if (drawY >= SCREEN_HEIGHT - 1) break;

            byte lineBits = fontData[fontOffset + row];

            for (int col = 0; col < 8; col++) {
                // Умножаем координату X на 2
                int drawX = (x + col) * 2;
                if (drawX >= SCREEN_WIDTH - 1) break;

                if (((lineBits >> (7 - col)) & 1) != 0) {
                    // Рисуем блок 2x2 пикселя в физическом буфере 960x540
                    vram[drawY * SCREEN_WIDTH + drawX] = color;
                    vram[drawY * SCREEN_WIDTH + (drawX + 1)] = color;
                    vram[(drawY + 1) * SCREEN_WIDTH + drawX] = color;
                    vram[(drawY + 1) * SCREEN_WIDTH + (drawX + 1)] = color;
                }
            }
        }
    }
    
    @Override
    public void tick(long cycles) {
        // === ИСПРАВЛЕНИЕ: Периодическая синхронизация VRAM ===
        // Это позволяет увидеть результат прямой записи в память (BIOS)
        scanoutTimer += cycles;
        if (scanoutTimer >= SCANOUT_INTERVAL) {
            if (vramDirty) {
                syncToScreenState();
            }
            scanoutTimer = 0;
        }
        // =====================================================

        if (status == 0) return;

        cyclesRemaining -= cycles;

        if (cyclesRemaining <= 0) {
            finishCommand();
            status = 0;
            mmioRegisters[0] = 0;
            cyclesRemaining = 0;
        }
    }
    
    private void finishCommand() {
        switch (currentCmd) {
            case CMD_CLEAR:
                Arrays.fill(vram, convertColor(rColor));
                vramDirty = true; // Помечаем как измененный
                break;
            
            case CMD_FILL_RECT:
                fillRect(rX, rY, rW, rH, convertColor(rColor));
                vramDirty = true; // Помечаем как измененный
                break;

            case CMD_DRAW_CHAR: // <--- Обработка новой команды
                // rW используется как код символа (так же, как в Python регистр REG_ARG_2)
                drawChar(rX, rY, rW & 0x7FF, convertColor(rColor));
                vramDirty = true;
                break;
            case CMD_SCROLL:
                // rColor берется из регистра REG_ARG_4 (индекс 6)
                // Мы сдвигаем экран на 16 пикселей вверх
                scrollScreenUp(16, convertColor(rColor));
                vramDirty = true;
                break;
        }
        syncToScreenState();
    }

    /**
     * Сдвигает содержимое VRAM вверх на указанное количество строк.
     * @param lines Количество строк (пикселей по вертикали)
     * @param fillOriginalColor Цвет, которым зальется пустое место снизу
     */
    private void scrollScreenUp(int lines, int fillOriginalColor) {
        // Вычисляем, сколько пикселей нужно сдвинуть
        int pixelsToShift = lines * SCREEN_WIDTH;

        if (pixelsToShift >= vram.length) {
            // Если сдвиг больше экрана, просто очищаем всё
            Arrays.fill(vram, fillOriginalColor);
        } else {
            // 1. Сдвигаем массив vram сам в себя:
            // Source: vram, начиная с pixelsToShift
            // Dest: vram, начиная с 0
            // Length: общий размер минус сдвиг
            System.arraycopy(vram, pixelsToShift, vram, 0, vram.length - pixelsToShift);

            // 2. Очищаем (заливаем цветом фона) появившуюся пустую область снизу
            int startIndex = vram.length - pixelsToShift;
            Arrays.fill(vram, startIndex, vram.length, fillOriginalColor);
        }
    }
    
    private void fillRect(int x, int y, int w, int h, int color) {
        int startX = Math.max(0, x * 2);
        int startY = Math.max(0, y * 2);
        int endX = Math.min(SCREEN_WIDTH, x + w);
        int endY = Math.min(SCREEN_HEIGHT, y + h);
        
        if (startX >= endX || startY >= endY) return;
        
        for (int cy = startY; cy < endY; cy++) {
            int offset = cy * SCREEN_WIDTH + startX;
            int length = endX - startX;
            Arrays.fill(vram, offset, offset + length, color);
        }
    }
    
    private int convertColor(int color) {
        if (tier == GpuTier.TIER3) {
            return color;
        }
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (tier == GpuTier.TIER1) {
            int lum = (r + g + b) / 3;
            int v = lum < 128 ? 0 : 255;
            int rgb = (v << 16) | (v << 8) | v;
            return (a << 24) | rgb;
        }
        int r3 = (r >> 5) & 0x07;
        int g3 = (g >> 5) & 0x07;
        int b2 = (b >> 6) & 0x03;
        int rr = (r3 * 255) / 7;
        int gg = (g3 * 255) / 7;
        int bb = (b2 * 255) / 3;
        int rgb = (rr << 16) | (gg << 8) | bb;
        return (a << 24) | rgb;
    }
    
    // Флаг для отслеживания изменений VRAM
    private boolean vramDirty = true;
    private int[] lastVramSnapshot = null;
    
    /**
     * Синхронизирует VRAM с ServerScreenState (конвертирует int[] в byte[]).
     * Оптимизированная версия: использует System.arraycopy и проверяет изменения.
     */
    private void syncToScreenState() {
        byte[] pixelBuffer = screenState.getPixelBuffer();
        if (pixelBuffer == null) return;

        // Преобразуем int[] VRAM (ARGB/RGBA) в byte[] (RGBA для OpenGL)
        // Используем 480x270 для экономии трафика (даунскейл), или полный размер
        // Сейчас передаем ПОЛНЫЙ буфер 960x540, так как ServerScreenState инициализирован на 480x270 по умолчанию.
        // !!! ВАЖНО: ServerScreenState должен быть инициализирован с правильным размером или мы должны даунскейлить.
        // VirtualMachineManager создает ScreenState с ServerFont.SCREEN_WIDTH (480).
        // Поэтому здесь делаем простой даунскейлинг (берем каждый второй пиксель), чтобы влезть в пакет.

        int targetW = 480;
        int targetH = 270;

        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                // Берем пиксель из VRAM (координаты * 2)
                int vramPixel = vram[(y * 2) * SCREEN_WIDTH + (x * 2)];

                int idx = (y * targetW + x) * 4;
                pixelBuffer[idx] = (byte) ((vramPixel >> 16) & 0xFF);     // R
                pixelBuffer[idx + 1] = (byte) ((vramPixel >> 8) & 0xFF);  // G
                pixelBuffer[idx + 2] = (byte) (vramPixel & 0xFF);         // B
                pixelBuffer[idx + 3] = (byte) ((vramPixel >> 24) & 0xFF); // A
            }
        }

        vramDirty = false;
        screenState.markDirty();
    }

    public int[] getVram() { return vram; }
}
