package com.loracore.computer;

import com.loracore.LoraCoreMod;

import java.util.Arrays;

/**
 * GPU устройство с MMIO, связанное с ServerScreenState через SystemBus.
 * VRAM находится в адресном пространстве 0x400000 - 0x47A120 (960x540 пикселей).
 * 
 * Реализует IMemoryMappedDevice и ITickable для работы с шиной.
 */
public class GpuMmioDevice implements IMemoryMappedDevice, ITickable {
    
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
    private static final int CMD_COPY_RECT = 3;
    
    // Внутреннее состояние GPU
    private int status = 0;       // 0 = Idle
    private int currentCmd = 0;
    private int rX, rY, rW, rH, rColor, rSrc, rDest; // Теневые регистры для исполнения
    private int cyclesRemaining = 0; // Сколько тактов осталось до завершения операции
    
    // Регистры MMIO (сырые данные)
    private final int[] mmioRegisters = new int[16];
    
    // VRAM как массив int (RGBA пиксели)
    private final int[] vram;
    
    // Ссылка на ServerScreenState для синхронизации
    private final ServerScreenState screenState;
    
    public GpuMmioDevice(ServerScreenState screenState) {
        this.screenState = screenState;
        this.vram = new int[VRAM_SIZE];
        this.registersOffset = VRAM_SIZE_BYTES; // VRAM в байтах
        
        // Инициализируем VRAM черным цветом
        Arrays.fill(vram, 0xFF000000); // RGBA: черный с полной непрозрачностью
        
        // Синхронизируем с ServerScreenState
        syncToScreenState();
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
        // 1. Запись в VRAM (Пиксели)
        if (offset < registersOffset) {
            int idx = offset / 4;
            int shift = (offset % 4) * 8;
            if (idx >= 0 && idx < vram.length) {
                int mask = ~(0xFF << shift);
                vram[idx] = (vram[idx] & mask) | ((value & 0xFF) << shift);
                // Помечаем VRAM как измененный
                vramDirty = true;
                // Помечаем экран как dirty при изменении VRAM
                screenState.markDirty();
            }
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
        
        // Триггер команды при записи в REG_CMD (когда записан последний байт)
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
        if (localOffset == REG_WIDTH) return SCREEN_WIDTH;
        if (localOffset == REG_HEIGHT) return SCREEN_HEIGHT;
        
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
                // Помечаем VRAM как измененный
                vramDirty = true;
                // Помечаем экран как dirty
                screenState.markDirty();
            }
            return;
        }
        
        // Запись в регистры MMIO
        int localOffset = offset - registersOffset;
        int regIdx = localOffset >> 2;
        
        if (regIdx >= 0 && regIdx < mmioRegisters.length) {
            mmioRegisters[regIdx] = value;
            // Триггер команды при записи в REG_CMD
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
            case CMD_COPY_RECT:
                pixels = rW * rH;
                break;
            default:
                return; // Неизвестная команда
        }
        
        // Вычисляем, сколько тиков займет операция (упрощенная модель: 128 пикселей за такт)
        if (pixels > 0) {
            cyclesRemaining = pixels / 128;
            if (cyclesRemaining < 1) cyclesRemaining = 1; // Минимум 1 такт
            
            status = 1; // BUSY
            mmioRegisters[0] = 1; // Sync register array immediately
        }
        
        LoraCoreMod.LOGGER.debug("[GPU] Executing CMD {} (X:{} Y:{} W:{} H:{} Color:0x{})",
                currentCmd, rX, rY, rW, rH, String.format("%08X", rColor));
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
                vramDirty = true; // Помечаем как измененный
                break;
            
            case CMD_FILL_RECT:
                fillRect(rX, rY, rW, rH, rColor);
                vramDirty = true; // Помечаем как измененный
                break;
            
            case CMD_COPY_RECT:
                // TODO: Реализовать копирование прямоугольника
                vramDirty = true;
                break;
        }
        
        // Синхронизируем с ServerScreenState
        syncToScreenState();
    }
    
    private void fillRect(int x, int y, int w, int h, int color) {
        int startX = Math.max(0, x);
        int startY = Math.max(0, y);
        int endX = Math.min(SCREEN_WIDTH, x + w);
        int endY = Math.min(SCREEN_HEIGHT, y + h);
        
        if (startX >= endX || startY >= endY) return;
        
        for (int cy = startY; cy < endY; cy++) {
            int offset = cy * SCREEN_WIDTH + startX;
            int length = endX - startX;
            Arrays.fill(vram, offset, offset + length, color);
        }
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
        
        // Проверяем, изменился ли VRAM (оптимизация)
        if (!vramDirty && lastVramSnapshot != null) {
            // Быстрая проверка: сравниваем только первые и последние элементы
            if (vram.length > 0 && lastVramSnapshot.length == vram.length) {
                if (vram[0] == lastVramSnapshot[0] && 
                    vram[vram.length - 1] == lastVramSnapshot[vram.length - 1]) {
                    // Вероятно, ничего не изменилось, но для надежности делаем полную проверку
                    boolean changed = false;
                    for (int i = 0; i < Math.min(100, vram.length); i++) {
                        if (vram[i] != lastVramSnapshot[i]) {
                            changed = true;
                            break;
                        }
                    }
                    if (!changed) {
                        return; // Ничего не изменилось, пропускаем синхронизацию
                    }
                }
            }
        }
        
        // Конвертируем int[] vram в byte[] pixelBuffer (RGBA)
        // Оптимизация: используем прямой доступ к массиву без проверок в цикле
        int maxPixels = Math.min(vram.length, pixelBuffer.length / 4);
        for (int i = 0; i < maxPixels; i++) {
            int pixel = vram[i];
            int idx = i * 4;
            pixelBuffer[idx] = (byte) ((pixel >> 16) & 0xFF);     // R
            pixelBuffer[idx + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            pixelBuffer[idx + 2] = (byte) (pixel & 0xFF);        // B
            pixelBuffer[idx + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }
        
        // Сохраняем снимок для следующей проверки
        if (lastVramSnapshot == null || lastVramSnapshot.length != vram.length) {
            lastVramSnapshot = new int[vram.length];
        }
        System.arraycopy(vram, 0, lastVramSnapshot, 0, vram.length);
        
        vramDirty = false;
        screenState.markDirty();
    }
    
    /**
     * Прямой доступ к VRAM (для отладки).
     */
    public int[] getVram() {
        return vram;
    }
}
