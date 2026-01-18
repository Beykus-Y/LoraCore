package com.loracore.lang;

import com.loracore.computer.InstructionSet;
import com.loracore.computer.SystemBus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Assembler {
    private final List<Integer> code = new ArrayList<>();
    private final ByteArrayOutputStream codeBuffer = new ByteArrayOutputStream();

    // Формат инструкции: [Opcode:8][Rd:4][Rs:4][Imm16:16]
    private static int enc(int op, int rd, int rs, int imm16) {
        return ((op & 0xFF) << 24)
                | ((rd & 0x0F) << 20)
                | ((rs & 0x0F) << 16)
                | (imm16 & 0xFFFF);
    }

    // --- Группа 1: Передача данных ---
    public Assembler mov(int rd, int rs) { code.add(enc(InstructionSet.OP_MOV, rd, rs, 0)); return this; }
    public Assembler ldi(int rd, int imm16) { code.add(enc(InstructionSet.OP_LDI, rd, 0, imm16)); return this; }
    public Assembler lui(int rd, int imm16) { code.add(enc(InstructionSet.OP_LUI, rd, 0, imm16)); return this; }
    public Assembler ld(int rd, int rAddr) { code.add(enc(InstructionSet.OP_LD, rd, rAddr, 0)); return this; }
    public Assembler st(int rAddr, int rs) { code.add(enc(InstructionSet.OP_ST, rAddr, rs, 0)); return this; }
    public Assembler push(int rs) { code.add(enc(InstructionSet.OP_PUSH, 0, rs, 0)); return this; }
    public Assembler pop(int rd) { code.add(enc(InstructionSet.OP_POP, rd, 0, 0)); return this; }

    // --- Группа 2: Арифметика ---
    public Assembler add(int rd, int rs) { code.add(enc(InstructionSet.OP_ADD, rd, rs, 0)); return this; }
    public Assembler sub(int rd, int rs) { code.add(enc(InstructionSet.OP_SUB, rd, rs, 0)); return this; }
    public Assembler mul(int rd, int rs) { code.add(enc(InstructionSet.OP_MUL, rd, rs, 0)); return this; }
    public Assembler div(int rd, int rs) { code.add(enc(InstructionSet.OP_DIV, rd, rs, 0)); return this; }
    public Assembler mod(int rd, int rs) { code.add(enc(InstructionSet.OP_MOD, rd, rs, 0)); return this; }

    // === ДОБАВЛЕНО: Арифметика с константами (Immediate) ===
    // Именно этого метода не хватало для BiosGenerator
    public Assembler addi(int rd, int imm16) { code.add(enc(InstructionSet.OP_ADDI, rd, 0, imm16)); return this; }
    public Assembler subi(int rd, int imm16) { code.add(enc(InstructionSet.OP_SUBI, rd, 0, imm16)); return this; }

    // --- Группа 3: Логика ---
    public Assembler and(int rd, int rs) { code.add(enc(InstructionSet.OP_AND, rd, rs, 0)); return this; }
    public Assembler or(int rd, int rs) { code.add(enc(InstructionSet.OP_OR, rd, rs, 0)); return this; }
    public Assembler xor(int rd, int rs) { code.add(enc(InstructionSet.OP_XOR, rd, rs, 0)); return this; }
    public Assembler not(int rd) { code.add(enc(InstructionSet.OP_NOT, rd, 0, 0)); return this; }
    public Assembler shl(int rd, int count) { code.add(enc(InstructionSet.OP_SHL, rd, 0, count)); return this; }
    public Assembler shr(int rd, int count) { code.add(enc(InstructionSet.OP_SHR, rd, 0, count)); return this; }

    // === ДОБАВЛЕНО: Логика с константами ===
    public Assembler andi(int rd, int imm16) { code.add(enc(InstructionSet.OP_ANDI, rd, 0, imm16)); return this; }
    public Assembler ori(int rd, int imm16) { code.add(enc(InstructionSet.OP_ORI, rd, 0, imm16)); return this; }

    // --- Группа 4: Управление потоком ---
    public Assembler cmp(int rd, int rs) { code.add(enc(InstructionSet.OP_CMP, rd, rs, 0)); return this; }
    public Assembler cmpi(int rd, int imm16) { code.add(enc(InstructionSet.OP_CMPI, rd, 0, imm16)); return this; }
    public Assembler jmp(int addr) { code.add(enc(InstructionSet.OP_JMP, 0, 0, addr)); return this; }
    public Assembler jz(int addr) { code.add(enc(InstructionSet.OP_JZ, 0, 0, addr)); return this; }
    public Assembler jnz(int addr) { code.add(enc(InstructionSet.OP_JNZ, 0, 0, addr)); return this; }
    public Assembler jg(int addr) { code.add(enc(InstructionSet.OP_JG, 0, 0, addr)); return this; }
    public Assembler jl(int addr) { code.add(enc(InstructionSet.OP_JL, 0, 0, addr)); return this; }
    public Assembler call(int addr) { code.add(enc(InstructionSet.OP_CALL, 0, 0, addr)); return this; }
    public Assembler ret() { code.add(enc(InstructionSet.OP_RET, 0, 0, 0)); return this; }

    // --- Группа 5: Система ---
    public Assembler waitCycles(int cycles) { code.add(enc(InstructionSet.OP_WAIT, 0, 0, cycles)); return this; }
    public Assembler hlt() { code.add(enc(InstructionSet.OP_HLT, 0, 0, 0)); return this; }
    public Assembler dump() { code.add(enc(InstructionSet.OP_DUMP, 0, 0, 0)); return this; }

    // --- Утилиты ---

    public void reset() {
        codeBuffer.reset();
    }

    /**
     * Возвращает текущее смещение (адрес следующей инструкции) в байтах.
     */
    public int getCurrentOffset() {
        return codeBuffer.size();
    }

    /**
     * Возвращает сгенерированный байт-код.
     */
    public byte[] toByteArray() {
        return codeBuffer.toByteArray();
    }

    /**
     * Возвращает массив "инструкций" (для старой логики подсчета длины через toIntArray().length).
     * 1 инструкция = 4 байта.
     */
    public int[] toIntArray() {
        return new int[codeBuffer.size() / 4];
    }

    /**
     * Генерирует 32-битную инструкцию.
     * Формат в памяти (Little Endian):
     * [0]: Imm Low
     * [1]: Imm High
     * [2]: (rD << 4) | rS
     * [3]: Opcode
     *
     * @param opcode Код операции (см. InstructionSet)
     * @param rD Регистр назначения (0-15)
     * @param rS Регистр источника (0-15)
     * @param imm16 Непосредственное значение (16 бит)
     */
    public void emit(int opcode, int rD, int rS, int imm16) {
        // Записываем 4 байта
        codeBuffer.write(imm16 & 0xFF);          // Byte 0: Младший байт Imm
        codeBuffer.write((imm16 >> 8) & 0xFF);   // Byte 1: Старший байт Imm
        codeBuffer.write(((rD & 0xF) << 4) | (rS & 0xF)); // Byte 2: Регистры
        codeBuffer.write(opcode & 0xFF);         // Byte 3: Опкод
    }
    public void emitInt(int value) {
        // Запись прямого 32-битного числа (для данных .data)
        codeBuffer.write(value & 0xFF);
        codeBuffer.write((value >> 8) & 0xFF);
        codeBuffer.write((value >> 16) & 0xFF);
        codeBuffer.write((value >> 24) & 0xFF);
    }

    /**
     * Алиас для emit, используемый TextAssembler.
     */
    public void emitGeneric(int opcode, int rD, int rS, int imm16) {
        emit(opcode, rD, rS, imm16);
    }

    /**
     * Патчит (перезаписывает) адрес перехода в уже сгенерированном коде.
     * Используется для backpatching в ручном генераторе.
     *
     * @param instructionOffset Смещение инструкции в БАЙТАХ (возвращается getCurrentOffset())
     * @param targetAddress Адрес, куда нужно прыгнуть (значение imm16)
     */
    public void patchJump(int instructionOffset, int targetAddress) {
        byte[] currentData = codeBuffer.toByteArray();

        if (instructionOffset < 0 || instructionOffset + 1 >= currentData.length) {
            throw new IllegalArgumentException("Invalid patch offset: " + instructionOffset);
        }

        // Перезаписываем первые 2 байта инструкции (Imm16)
        currentData[instructionOffset] = (byte) (targetAddress & 0xFF);
        currentData[instructionOffset + 1] = (byte) ((targetAddress >> 8) & 0xFF);

        // Пересоздаем буфер с новыми данными (немного неэффективно, но надежно)
        codeBuffer.reset();
        try {
            codeBuffer.write(currentData);
        } catch (IOException e) {
            // Should never happen with ByteArrayOutputStream
            throw new RuntimeException(e);
        }
    }
}