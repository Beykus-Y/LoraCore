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
    public long totalCycles = 0; // Реальный счетчик тактов с момента старта
    private final SystemBus bus;
    private final GenericRam systemRam; // Прямая ссылка на RAM для performBitFlip
    private final int cpuId;

    private static final int FLAG_ZERO = 1;
    private static final int FLAG_NEGATIVE = 2;
    private boolean interruptsEnabled = false; // По умолчанию выключены

    public int cr3 = 0;             // Физический адрес начала таблицы страниц
    public boolean pagingEnabled = false;

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
        totalCycles++;
        if (interruptsEnabled) {
            int irq = bus.checkPendingInterrupts();
            if (irq != -1) {
                handleInterrupt(irq);
                if (halted) halted = false; // Будим процессор
            }
        }
        if (halted) return 1;

        // 1. Обновляем физику и проверяем стабильность раз в 256 тактов
        if ((cycleCounter++ & 0xFF) == 0) {
            updatePhysics();
            if (checkInstability()) {
                errorCount++;
            }
        }
        if (this.pc == 0x11A0) {
            int instr = readVirtualInt(pc);
            int opcode = (instr >>> 24) & 0xFF;
            System.out.printf("CPU HALTED AT DEBUG POINT 0x11A0! Instruction: 0x%08X, Opcode: 0x%02X\n", instr, opcode);
            // Выведи значения регистров, чтобы понять контекст
            dumpRegisters(pc);
        }

        // 2. Fetch
        int instr = readVirtualInt(pc);
        
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
            case InstructionSet.OP_LD -> {
                int vAddr = registers[rS];
                registers[rD] = readVirtualInt(vAddr);
            }
            case InstructionSet.OP_ST -> {
                int vAddr = registers[rD];
                writeVirtualInt(vAddr, registers[rS]);
            }
            case InstructionSet.OP_PUSH -> {
                registers[15] -= 4;
                writeVirtualInt(registers[15], registers[rS]);
            }
            case InstructionSet.OP_POP -> {
                registers[rD] = readVirtualInt(registers[15]);
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
            case InstructionSet.OP_POW -> {
                // Используем Math.pow, приводим к int
                registers[rD] = (int) Math.pow(registers[rD], registers[rS]);
                updateFlags(registers[rD]);
            }

            // --- Группа 3: Логика ---
            case InstructionSet.OP_AND  -> { registers[rD] &= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_ANDI -> { registers[rD] &= imm16; updateFlags(registers[rD]); }
            case InstructionSet.OP_OR   -> { registers[rD] |= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_ORI  -> { registers[rD] |= imm16; updateFlags(registers[rD]); }
            case InstructionSet.OP_XOR  -> { registers[rD] ^= registers[rS]; updateFlags(registers[rD]); }
            case InstructionSet.OP_NOT  -> { registers[rD] = ~registers[rD]; updateFlags(registers[rD]); }
            case InstructionSet.OP_SHL -> {
                int count = (imm16 != 0) ? (imm16 & 0x1F) : (registers[rS] & 0x1F);
                registers[rD] <<= count;
                updateFlags(registers[rD]);
            }
            case InstructionSet.OP_SHR -> {
                int count = (imm16 != 0) ? (imm16 & 0x1F) : (registers[rS] & 0x1F);
                registers[rD] >>>= count; // Логический сдвиг
                updateFlags(registers[rD]);
            }

            // --- Группа 4: Управление потоком ---
            case InstructionSet.OP_CMP  -> updateFlags(registers[rD] - registers[rS]);
            case InstructionSet.OP_CMPI -> updateFlags(registers[rD] - simm16);
            case InstructionSet.OP_JMP  -> pc = imm16;
            case InstructionSet.OP_JZ   -> { if ((flags & FLAG_ZERO) != 0) pc = imm16; }
            case InstructionSet.OP_JNZ  -> { if ((flags & FLAG_ZERO) == 0) pc = imm16; }
            case InstructionSet.OP_JG   -> { if ((flags & FLAG_ZERO) == 0 && (flags & FLAG_NEGATIVE) == 0) pc = imm16; }
            case InstructionSet.OP_JL   -> { if ((flags & FLAG_NEGATIVE) != 0) pc = imm16; }
            case InstructionSet.OP_CALL -> {
                // 1. Уменьшаем виртуальный SP
                registers[15] -= 4;

                // 2. Пишем адрес возврата через MMU (включая границу страниц)
                writeVirtualInt(registers[15], pc);

                // 4. Прыгаем на адрес (imm16 обычно содержит абсолютный адрес в этой архитектуре)
                pc = imm16;
            }
            case InstructionSet.OP_RET -> {
                // 1. Читаем из виртуального стека адрес возврата
                pc = readVirtualInt(registers[15]);

                // 3. Увеличиваем виртуальный SP
                registers[15] += 4;
            }

            // --- Группа 5: Система ---
            case InstructionSet.OP_HLT  -> halted = true;
            case InstructionSet.OP_DUMP -> dumpRegisters(currentPc);
            case InstructionSet.OP_WAIT -> { return simm16 > 0 ? simm16 : 1; }
            case InstructionSet.OP_INT -> {
                int interruptCode = imm16; // Например, INT 0x21
                // Мы можем передать это событие в VirtualMachine, чтобы она выполнила действие
                // Например, печать строки, если это "сервисное прерывание"
                bus.handleInterrupt(interruptCode, registers);
            }
            case InstructionSet.OP_OUT -> {
                int port = imm16; // Порт из инструкции (или можно взять из регистра)
                int value = registers[rS];
                // Передаем шине: "запиши в порт X значение Y"
                bus.writePort(port, value);
            }
            case InstructionSet.OP_IN -> {
                int port = imm16;
                registers[rD] = bus.readPort(port);
            }
            case InstructionSet.OP_LDO -> {
                int vAddr = registers[rS] + simm16;
                registers[rD] = readVirtualInt(vAddr);
            }
            case InstructionSet.OP_STO -> {
                int vAddr = registers[rD] + simm16;
                writeVirtualInt(vAddr, registers[rS]);
            }


            // --- Группа 6: Физика и Оверклокинг ---
            case InstructionSet.OP_GET_TEMP -> registers[rD] = tempmC / 10;
            case InstructionSet.OP_SET_VOLT -> voltageMV = registers[rS];
            case InstructionSet.OP_GET_CLOCK -> {
                registers[rD] = (int) totalCycles;
            }
            case InstructionSet.OP_CPUID -> {
                // В R_dest возвращаем инфо, зависящее от того, что лежит в R_src
                int request = registers[rS];
                switch (request) {
                    case 0 -> registers[rD] = cpuId;             // Уникальный ID чипа
                    case 1 -> registers[rD] = freqHz;            // Частота
                    case 2 -> registers[rD] = stabilityLimit;    // Предел разгона
                    case 3 -> registers[rD] = 0x0001;            // Версия архитектуры Lora-1
                    default -> registers[rD] = 0;
                }
            }
            case InstructionSet.OP_JMPR -> {
                // Прыгаем на адрес из регистра rD
                pc = registers[rD];
            }
            case InstructionSet.OP_STI -> interruptsEnabled = true;
            case InstructionSet.OP_CLI -> interruptsEnabled = false;
            case InstructionSet.OP_IRET -> {
                // --- 1. Извлекаем Флаги ---
                flags = readVirtualInt(registers[15]);
                registers[15] += 4;

                // --- 2. Извлекаем PC (Адрес возврата) ---
                pc = readVirtualInt(registers[15]);
                registers[15] += 4;

                // --- 3. Включаем прерывания обратно ---
                this.interruptsEnabled = true;
            }

            case InstructionSet.OP_SET_CR3 -> {
                this.cr3 = registers[rS];
            }
            case InstructionSet.OP_PG_ENABLE -> {
                this.pagingEnabled = true;
            }
            case InstructionSet.OP_PG_DISABLE -> {
                this.pagingEnabled = false;
            }

            default ->{
                LoraCoreMod.LOGGER.warn(String.format("[CPU FAULT] Unknown Opcode: 0x%02X at PC: 0x%04X (Instr: 0x%08X)",
                        opcode, currentPc, instr));
                errorCount++;
            }
        }

        return 1;
    }

    public void reset() {
        this.pc = 0;
        this.flags = 0;
        this.halted = false; // <--- САМОЕ ВАЖНОЕ
        this.cr3 = 0;
        this.pagingEnabled = false;
        this.errorCount = 0;
        this.cycleCounter = 0;
        this.emptyMemoryCounter = 0;
        this.totalCycles = 0;
        this.interruptsEnabled = false;
        this.bus.clearPendingInterrupts();
        // Очистка регистров (опционально, но полезно)
        java.util.Arrays.fill(this.registers, 0);
        this.registers[15] = 0x0FFC; // Reset SP
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

    /**
     * Трансляция виртуального адреса в физический.
     */
    private int translate(int virtualAddr) {
        if (!pagingEnabled) return virtualAddr;

        // 1. Извлекаем индекс страницы (верхние 20 бит)
        int pageIndex = virtualAddr >>> 12;
        // 2. Извлекаем смещение внутри страницы (нижние 12 бит)
        int offset = virtualAddr & 0xFFF;

        // 3. Читаем PTE (Page Table Entry) из физической памяти
        // Таблица — это массив 32-битных чисел по адресу CR3
        int pteAddr = cr3 + (pageIndex * 4);

        // Читаем напрямую из шины, игнорируя текущий статус пейджинга (это системный доступ)
        // Мы используем bus.readInt, так как таблицы лежат в физической RAM
        int pte = bus.readInt(pteAddr);

        // 4. Проверка бита присутствия (бит 0: Present)
        if ((pte & 1) == 0) {
            // Если страницы нет в памяти — вызываем аппаратное исключение (Page Fault)
            throw new HardwareInterruptException("PAGE FAULT: Access to 0x" +
                    Integer.toHexString(virtualAddr) + " is not mapped!");
        }

        // 5. Собираем физический адрес
        // Базовый адрес фрейма (из PTE) + смещение
        int physicalFrame = pte & 0xFFFFF000;
        return physicalFrame | offset;
    }

    /**
     * Reads a little-endian 32-bit value from virtual memory. A value that
     * crosses a page boundary must translate each byte independently because
     * adjacent virtual pages are not required to use adjacent physical frames.
     */
    private int readVirtualInt(int virtualAddr) {
        if (!pagingEnabled || (virtualAddr & 0xFFF) <= 0xFFC) {
            return bus.readInt(translate(virtualAddr));
        }

        return (bus.readByte(translate(virtualAddr)) & 0xFF)
                | ((bus.readByte(translate(virtualAddr + 1)) & 0xFF) << 8)
                | ((bus.readByte(translate(virtualAddr + 2)) & 0xFF) << 16)
                | ((bus.readByte(translate(virtualAddr + 3)) & 0xFF) << 24);
    }

    /** Writes a little-endian 32-bit value to virtual memory. */
    private void writeVirtualInt(int virtualAddr, int value) {
        if (!pagingEnabled || (virtualAddr & 0xFFF) <= 0xFFC) {
            bus.writeInt(translate(virtualAddr), value);
            return;
        }

        bus.writeByte(translate(virtualAddr), (byte) value);
        bus.writeByte(translate(virtualAddr + 1), (byte) (value >>> 8));
        bus.writeByte(translate(virtualAddr + 2), (byte) (value >>> 16));
        bus.writeByte(translate(virtualAddr + 3), (byte) (value >>> 24));
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

    private void handleInterrupt(int irq) {
        // 1. Сбрасываем сигнал в контроллере прерываний
        bus.clearInterrupt(irq);

        // 2. Выключаем прерывания, чтобы не возникло "рекурсии" прерываний
        this.interruptsEnabled = false;

        // 3. Сохраняем PC в стек.
        // ВАЖНО: сначала уменьшаем виртуальный SP, потом транслируем его
        registers[15] -= 4;
        writeVirtualInt(registers[15], pc);

        // 4. Сохраняем Flags в стек
        registers[15] -= 4;
        writeVirtualInt(registers[15], flags);

        // 5. Читаем адрес обработчика из таблицы векторов (IVT)
        // IVT обычно находится в физической памяти по адресу 0x0000.
        // Если твоё ядро замапило виртуальный 0x0 на физический 0x0 (Identity Map),
        // то вызываем translate, чтобы соблюдать правила MMU.
        pc = readVirtualInt(irq * 4);

    }
}
