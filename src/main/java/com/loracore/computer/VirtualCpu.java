package com.loracore.computer;

import com.loracore.LoraCoreMod;

/**
 * Виртуальный процессор с физикой (нагрев, вольтаж, Silicon Lottery).
 * High Performance Implementation: Zero-allocation в горячем цикле.
 * 
 * Физика в Fixed Point (милливольты, миллиградусы) для избежания double.
 */
public class VirtualCpu {
    public final int[] registers = new int[16];
    public int pc = 0;
    public int flags = 0;
    public long errorCount = 0;

    // Физика в Fixed Point (милливольты, миллиградусы)
    public int tempmC = 25000;  // 25.0 C (в миллиградусах)
    public int voltageMV = 1200; // 1.2 V (в милливольтах)
    public int freqHz = 100000;  // 100 kHz
    public int stabilityLimit = 600000; // Базовый предел кремния (в Hz)

    private int rngState; // Xorshift state для генерации bit flips
    private int cycleCounter = 0;
    private boolean halted = false;
    private int emptyMemoryCounter = 0; // Счетчик пустых инструкций подряд

    private final SystemBus bus;
    private final GenericRam systemRam; // Прямая ссылка на RAM для performBitFlip
    private final int cpuId;

    private static final int FLAG_ZERO = 1;
    private static final int FLAG_NEGATIVE = 2;

    public VirtualCpu(SystemBus bus, GenericRam systemRam, CpuTiers.Config config, long seed) {
        this.bus = bus;
        this.systemRam = systemRam;
        this.rngState = (int) (seed ^ (seed >>> 32));
        if (this.rngState == 0) this.rngState = 0xACE1;
        this.registers[15] = 0x0FFC; // Stack Pointer (по умолчанию)
        
        // Применяем конфигурацию процессора
        this.voltageMV = (int)(config.voltage * 1000);
        this.freqHz = (int)config.targetFreq;
        this.stabilityLimit = (int)config.stabilityLimit;
        int derivedId = (int)(seed ^ (seed >>> 32));
        if (derivedId == 0) {
            derivedId = 1;
        }
        this.cpuId = derivedId;
    }

    public int getCpuId() {
        return cpuId;
    }

    /**
     * Выполняет одну инструкцию процессора.
     * @return Количество тактов, затраченных на выполнение (обычно 1)
     */
    public int step() {
        if (halted) return 1;

        // 1. Обновляем физику и проверяем стабильность раз в 256 тактов
        if ((cycleCounter++ & 0xFF) == 0) {
            updatePhysics();
            if (checkInstability()) {
                errorCount++;
            }
        }

        // 2. Fetch
        int instr = bus.readInt(pc);
        
        // Защита от пустой памяти: если инструкция 0 много раз подряд, останавливаем CPU
        if (instr == 0) {
            emptyMemoryCounter++;
            if (emptyMemoryCounter > 100) {
                // Если 100+ пустых инструкций подряд, останавливаем процессор
                halted = true;
                LoraCoreMod.LOGGER.debug("[CPU] Halted: too many empty instructions (PC: 0x{})",
                        String.format("%06X", pc));
                return 1;
            }
        } else {
            emptyMemoryCounter = 0; // Сбрасываем счетчик при непустой инструкции
        }
        
        int currentPc = pc;
        pc += 4;

        // 3. Decode
        int opcode = (instr >>> 24) & 0xFF;
        int rD = (instr >> 20) & 0x0F;
        int rS = (instr >> 16) & 0x0F;
        int imm16 = instr & 0xFFFF;
        short simm16 = (short) imm16;

        // 4. Execute
        switch (opcode) {
            case InstructionSet.OP_NOP -> {}

            // --- Группа 1: Передача данных ---
            case InstructionSet.OP_MOV -> registers[rD] = registers[rS];
            case InstructionSet.OP_LDI -> registers[rD] = imm16;
            case InstructionSet.OP_LUI -> registers[rD] = imm16 << 16;
            case InstructionSet.OP_LD  -> registers[rD] = bus.readInt(registers[rS]);
            case InstructionSet.OP_ST  -> bus.writeInt(registers[rD], registers[rS]);
            case InstructionSet.OP_PUSH -> {
                registers[15] -= 4;
                bus.writeInt(registers[15], registers[rS]);
            }
            case InstructionSet.OP_POP -> {
                registers[rD] = bus.readInt(registers[15]);
                registers[15] += 4;
            }

            // --- Группа 2: Арифметика ---
            case InstructionSet.OP_ADD  -> { registers[rD] += registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_SUB  -> { registers[rD] -= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_MUL  -> { registers[rD] *= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_DIV  -> {
                if (registers[rS] != 0) { registers[rD] /= registers[rS]; updateFlags(registers[rD]); }
                else errorCount++;
            }
            case InstructionSet.OP_MOD  -> {
                if (registers[rS] != 0) { registers[rD] %= registers[rS]; updateFlags(registers[rD]); }
                else errorCount++;
            }
            case InstructionSet.OP_ADDI -> { registers[rD] += simm16; updateFlags(registers[rD]); }
            case InstructionSet.OP_SUBI -> { registers[rD] -= simm16; updateFlags(registers[rD]); }

            // --- Группа 3: Логика ---
            case InstructionSet.OP_AND  -> { registers[rD] &= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_ANDI -> { registers[rD] &= imm16; updateFlags(registers[rD]); }
            case InstructionSet.OP_OR   -> { registers[rD] |= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_ORI  -> { registers[rD] |= imm16; updateFlags(registers[rD]); }
            case InstructionSet.OP_XOR  -> { registers[rD] ^= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_NOT  -> { registers[rD] = ~registers[rD]; updateFlags(registers[rD]); }
            case InstructionSet.OP_SHL  -> { registers[rD] <<= (imm16 & 0x1F); updateFlags(registers[rD]); }
            case InstructionSet.OP_SHR  -> { registers[rD] >>>= (imm16 & 0x1F); updateFlags(registers[rD]); }

            // --- Группа 4: Управление потоком ---
            case InstructionSet.OP_CMP  -> updateFlags(registers[rD] - registers[rS]);
            case InstructionSet.OP_CMPI -> updateFlags(registers[rD] - simm16);
            case InstructionSet.OP_JMP  -> pc = imm16;
            case InstructionSet.OP_JZ   -> { if ((flags & FLAG_ZERO) != 0) pc = imm16; }
            case InstructionSet.OP_JNZ  -> { if ((flags & FLAG_ZERO) == 0) pc = imm16; }
            case InstructionSet.OP_JG   -> { if ((flags & FLAG_ZERO) == 0 && (flags & FLAG_NEGATIVE) == 0) pc = imm16; }
            case InstructionSet.OP_JL   -> { if ((flags & FLAG_NEGATIVE) != 0) pc = imm16; }
            case InstructionSet.OP_CALL -> {
                registers[15] -= 4;
                bus.writeInt(registers[15], pc);
                pc = imm16;
            }
            case InstructionSet.OP_RET  -> {
                pc = bus.readInt(registers[15]);
                registers[15] += 4;
            }

            // --- Группа 5: Система ---
            case InstructionSet.OP_HLT  -> halted = true;
            case InstructionSet.OP_DUMP -> dumpRegisters(currentPc);
            case InstructionSet.OP_WAIT -> { return simm16 > 0 ? simm16 : 1; }

            // --- Группа 6: Физика и Оверклокинг ---
            case InstructionSet.OP_GET_TEMP -> registers[rD] = tempmC / 10;
            case InstructionSet.OP_SET_VOLT -> voltageMV = registers[rS];
            case InstructionSet.OP_GET_CLOCK -> registers[rD] = freqHz;

            default ->{
                LoraCoreMod.LOGGER.warn(String.format("[CPU FAULT] Unknown Opcode: 0x%02X at PC: 0x%04X (Instr: 0x%08X)",
                        opcode, currentPc, instr));
                errorCount++;
            }
        }

        return 1;
    }

    private void updateFlags(int val) {
        flags = 0;
        if (val == 0) flags |= FLAG_ZERO;
        if (val < 0) flags |= FLAG_NEGATIVE;
    }

    /**
     * Обновляет физику процессора (нагрев, охлаждение).
     * Вызывается раз в 256 тактов для оптимизации.
     */
    private void updatePhysics() {
        int ambient = 20000; // 20.0 C (в миллиградусах)
        // Нагрев: (U^2 * F) / C
        // Используем long для избежания переполнения
        long heat = ((long) voltageMV * voltageMV / 1000) * (freqHz / 10000) / 10000;
        int cooling = (tempmC - ambient) / 512;
        tempmC += (int) (heat - cooling);
        
        // Защита от отрицательной температуры
        if (tempmC < ambient) {
            tempmC = ambient;
        }
    }

    /**
     * Проверяет нестабильность процессора (перегрев, разгон).
     * Если условия выполнены, вызывает performBitFlip().
     * @return true, если произошел bit flip
     */
    private boolean checkInstability() {
        // Логика нестабильности через Xorshift RNG
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 17);
        rngState ^= (rngState << 5);

        int failureChance = 0;
        // Перегрев > 85°C увеличивает шанс ошибки
        if (tempmC > 85000) {
            failureChance += (tempmC - 85000) / 100;
        }
        // Разгон выше лимита увеличивает шанс ошибки
        if (freqHz > stabilityLimit) {
            failureChance += (freqHz - stabilityLimit) / 1000;
        }

        if (failureChance > 0 && (rngState & 0xFFFF) < failureChance) {
            performBitFlip();
            return true;
        }
        return false;
    }

    /**
     * Выполняет случайный bit flip в системной памяти (RAM).
     * Это приводит к порче данных и может вызвать краш Lua VM или ядра.
     */
    private void performBitFlip() {
        if (systemRam == null) return;
        
        byte[] ram = systemRam.getRawMemory();
        if (ram == null || ram.length == 0) return;

        // Генерируем случайный адрес в RAM
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 17);
        rngState ^= (rngState << 5);
        
        int address = (rngState & 0x7FFFFFFF) % ram.length;
        int bit = rngState & 0x7; // 0-7 бит
        
        // Инвертируем случайный бит
        ram[address] ^= (1 << bit);
        
        LoraCoreMod.LOGGER.warn("[CPU] Bit flip at RAM address 0x{}, bit {} (Temp: {:.1f}°C, Freq: {} Hz)",
                String.format("%06X", address), bit, tempmC / 1000.0, freqHz);
    }

    private void dumpRegisters(int currentPc) {
        LoraCoreMod.LOGGER.debug("[CPU DUMP] PC:{} R0:{} R1:{} R7:{} R12:{} R13:{} SP:{} T:{:.2f}",
                String.format("%04X", currentPc),
                String.format("%08X", registers[0]),
                String.format("%08X", registers[1]),
                String.format("%08X", registers[7]),
                String.format("%08X", registers[12]),
                String.format("%08X", registers[13]),
                String.format("%04X", registers[15]),
                tempmC / 1000.0);
    }

    /**
     * Возвращает текущую температуру в градусах Цельсия.
     */
    public double getTemperatureCelsius() {
        return tempmC / 1000.0;
    }

    /**
     * Возвращает текущий вольтаж в вольтах.
     */
    public double getVoltage() {
        return voltageMV / 1000.0;
    }

    /**
     * Возвращает текущую частоту в Hz.
     */
    public int getFrequency() {
        return freqHz;
    }
}
