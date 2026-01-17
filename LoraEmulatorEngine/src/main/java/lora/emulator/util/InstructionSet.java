package lora.emulator.util;

/**
 * Набор инструкций 32-битного процессора Lora.
 * Формат инструкции (4 байта): [Opcode: 8][R_dest: 4][R_src: 4][Immediate: 16]
 */
public class InstructionSet {

    // --- Группа 0: Системные ---
    public static final int OP_NOP   = 0x00;

    // --- Группа 1: Передача данных (Data Movement) ---
    public static final int OP_MOV   = 0x01; // MOV R_dest, R_src (Копировать регистр)
    public static final int OP_LDI   = 0x02; // LDI R_dest, Value (Load Immediate 16-bit)
    public static final int OP_LD    = 0x03; // LD  R_dest, R_addr (Load from RAM)
    public static final int OP_ST    = 0x04; // ST  R_addr, R_src  (Store to RAM)
    public static final int OP_PUSH  = 0x05; // PUSH R_src
    public static final int OP_POP   = 0x06; // POP R_dest
    public static final int OP_LUI   = 0x07; // LUI R_dest, Value (Load Upper Immediate)

    // --- Группа 2: Арифметика (Math) ---
    public static final int OP_ADD   = 0x10; // ADD  R_dest, R_src
    public static final int OP_SUB   = 0x11; // SUB  R_dest, R_src
    public static final int OP_MUL   = 0x12; // MUL  R_dest, R_src
    public static final int OP_DIV   = 0x13; // DIV  R_dest, R_src
    public static final int OP_MOD   = 0x14; // MOD  R_dest, R_src
    public static final int OP_ADDI  = 0x15; // ADDI R_dest, Value (Add Immediate)
    public static final int OP_SUBI  = 0x16;
    // --- Группа 3: Логика (Bitwise) ---
    public static final int OP_AND   = 0x20; // AND R_dest, R_src
    public static final int OP_OR    = 0x21; // OR  R_dest, R_src
    public static final int OP_XOR   = 0x22; // XOR R_dest, R_src
    public static final int OP_NOT   = 0x23; // NOT R_dest
    public static final int OP_SHL   = 0x24; // SHL R_dest, Count
    public static final int OP_SHR   = 0x25; // SHR R_dest, Count
    public static final int OP_ANDI  = 0x26;
    public static final int OP_ORI   = 0x27;

    // --- Группа 4: Управление потоком (Branching) ---
    public static final int OP_CMP   = 0x30; // CMP  R_dest, R_src
    public static final int OP_JMP   = 0x31; // JMP  Address
    public static final int OP_JZ    = 0x32; // JZ   Address (Jump if Zero / Equal)
    public static final int OP_JNZ   = 0x33; // JNZ  Address (Jump if Not Zero / Not Equal)
    public static final int OP_JG    = 0x34; // JG   Address (Jump if Greater)
    public static final int OP_JL    = 0x35; // JL   Address (Jump if Less)
    public static final int OP_CALL  = 0x36; // CALL Address
    public static final int OP_RET   = 0x37; // RET
    public static final int OP_CMPI  = 0x38; // CMPI R_dest, Value (Compare Immediate)

    // --- Группа 5: Система и IO ---
    public static final int OP_IN    = 0x40; // IN  R_dest, Port
    public static final int OP_OUT   = 0x41; // OUT Port, R_src
    public static final int OP_INT   = 0x42; // INT Code
    public static final int OP_WAIT  = 0x43; // WAIT Cycles
    public static final int OP_HLT   = 0x44; // HLT (Halt)
    public static final int OP_DUMP  = 0x45; // DUMP (Debug Registers)

    // --- Группа 6: Overclocking & Physical ---
    public static final int OP_GET_TEMP  = 0x50; // GET_TEMP R_dest
    public static final int OP_SET_VOLT  = 0x51; // SET_VOLT R_src
    public static final int OP_GET_CLOCK = 0x52; // GET_CLOCK R_dest
    public static final int OP_CPUID = 0x53;

    /**
     * Вспомогательный метод для ассемблера.
     * Собирает 32-битную инструкцию из компонентов.
     */
    public static int asm(String opName, int rDest, int rSrc, int imm) {
        int opcode = switch (opName.toUpperCase()) {
            case "NOP"   -> OP_NOP;
            case "MOV"   -> OP_MOV;
            case "LDI"   -> OP_LDI;
            case "LD"    -> OP_LD;
            case "ST"    -> OP_ST;
            case "PUSH"  -> OP_PUSH;
            case "POP"   -> OP_POP;
            case "LUI"   -> OP_LUI;
            case "ADD"   -> OP_ADD;
            case "SUB"   -> OP_SUB;
            case "MUL"   -> OP_MUL;
            case "DIV"   -> OP_DIV;
            case "MOD"   -> OP_MOD;
            case "ADDI"  -> OP_ADDI;
            case "SUBI" -> OP_SUBI;
            case "AND"   -> OP_AND;
            case "OR"    -> OP_OR;
            case "ORI"   -> OP_ORI;
            case "XOR"   -> OP_XOR;
            case "NOT"   -> OP_NOT;
            case "SHL"   -> OP_SHL;
            case "SHR"   -> OP_SHR;
            case "CMP"   -> OP_CMP;
            case "CMPI"  -> OP_CMPI;
            case "JMP"   -> OP_JMP;
            case "JZ", "JE" -> OP_JZ;
            case "JNZ", "JNE" -> OP_JNZ;
            case "JG"    -> OP_JG;
            case "JL"    -> OP_JL;
            case "CALL"  -> OP_CALL;
            case "RET"   -> OP_RET;
            case "IN"    -> OP_IN;
            case "OUT"   -> OP_OUT;
            case "INT"   -> OP_INT;
            case "WAIT"  -> OP_WAIT;
            case "HLT"   -> OP_HLT;
            case "DUMP"  -> OP_DUMP;
            case "GET_TEMP"  -> OP_GET_TEMP;
            case "SET_VOLT"  -> OP_SET_VOLT;
            case "ANDI" -> OP_ANDI;
            case "CPUID" -> OP_CPUID;

            case "GET_CLOCK" -> OP_GET_CLOCK;
            default -> throw new IllegalArgumentException("Unknown instruction: " + opName);
        };

        return (opcode << 24) | ((rDest & 0xF) << 20) | ((rSrc & 0xF) << 16) | (imm & 0xFFFF);
    }
}