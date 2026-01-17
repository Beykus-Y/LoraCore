package lora.emulator.cpu;

import lora.emulator.bus.SystemBus;
import lora.emulator.util.InstructionSet;

/**
 * Lora Virtual CPU - High Performance Implementation.
 * Никаких double в горячем цикле. Никаких объектов. Только чистая арифметика.
 */
public class VirtualCpu {
    public final int[] registers = new int[16];
    public int pc = 0;
    public int flags = 0;
    public long errorCount = 0;

    // Физика в Fixed Point (милливольты, миллиградусы)
    public int tempmC = 25000;  // 25.0 C
    public int voltageMV = 1200; // 1.2 V
    public int freqHz = 100000;
    public int stabilityLimit = 600000; // Базовый предел кремния

    private int rngState; // Xorshift state
    private int cycleCounter = 0;
    private boolean halted = false;

    private final SystemBus bus;

    private static final int FLAG_ZERO = 1;
    private static final int FLAG_NEGATIVE = 2;

    public VirtualCpu(SystemBus bus, long seed) {
        this.bus = bus;
        this.rngState = (int) (seed ^ (seed >>> 32));
        if (this.rngState == 0) this.rngState = 0xACE1;
        this.registers[15] = 0x0FFC; // Stack Pointer (по умолчанию)
    }

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
                System.err.printf("[CPU FAULT] Unknown Opcode: 0x%02X at PC: 0x%04X (Instr: 0x%08X)\n",
                        opcode, currentPc, instr);
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

    private void updatePhysics() {
        int ambient = 20000; // 20.0 C
        // Нагрев: (U^2 * F) / C
        long heat = ((long) voltageMV * voltageMV / 1000) * (freqHz / 10000) / 10000;
        int cooling = (tempmC - ambient) / 512;
        tempmC += (int) (heat - cooling);
    }

    private boolean checkInstability() {
        // Логика нестабильности через Xorshift RNG
        rngState ^= (rngState << 13);
        rngState ^= (rngState >>> 17);
        rngState ^= (rngState << 5);

        int failureChance = 0;
        if (tempmC > 85000) failureChance += (tempmC - 85000) / 100;
        if (freqHz > stabilityLimit) failureChance += (freqHz - stabilityLimit) / 1000;

        if (failureChance > 0 && (rngState & 0xFFFF) < failureChance) {
            performBitFlip();
            return true;
        }
        return false;
    }

    private void performBitFlip() {
        int reg = (rngState >>> 8) & 0xF;
        int bit = rngState & 0x1F;
        registers[reg] ^= (1 << bit);
    }

    private void dumpRegisters(int currentPc) {
        System.out.printf("[CPU DUMP] PC:%04X R0:%08X R1:%08X R7:%08X R12:%08X R13:%08X SP:%04X T:%.2f\n",
                currentPc, registers[0], registers[1], registers[7], registers[12], registers[13], registers[15], tempmC / 1000.0);
    }
}