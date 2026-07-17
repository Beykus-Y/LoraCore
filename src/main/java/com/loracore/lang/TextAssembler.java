package com.loracore.lang;

import com.loracore.computer.InstructionSet;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Ассемблер для процессора LoraCore.
 * Преобразует текстовый ASM код в бинарный машинный код.
 */
public class TextAssembler {

    private static final Map<String, Integer> OPCODES = new HashMap<>();
    private final List<String> debugMap = new ArrayList<>();

    static {
        // Автоматическое заполнение карты опкодов через рефлексию из InstructionSet
        // Это гарантирует, что ассемблер всегда знает все инструкции
        try {
            for (Field field : InstructionSet.class.getFields()) {
                if (field.getName().startsWith("OP_")) {
                    String mnemonic = field.getName().substring(3); // Убираем "OP_"
                    int opcode = field.getInt(null);
                    OPCODES.put(mnemonic.toLowerCase(), opcode);
                }
            }
            // Алиасы (если нужны)
            OPCODES.put("move", InstructionSet.OP_MOV);
            OPCODES.put("halt", InstructionSet.OP_HLT);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize opcode map", e);
        }
    }

    // Таблица символов (метки адресов и константы)
    private final Map<String, Integer> symbolTable = new HashMap<>();

    // Список мест, где используются метки, которые еще не были определены
    private final List<PendingPatch> pendingPatches = new ArrayList<>();

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(buffer);

    private int currentAddress = 0;

    private record PendingPatch(int instructionAddress, String labelName) {}
    public byte[] compile(String source) throws IOException {
        return compile(source, 0);
    }
    public byte[] compile(String source, int baseAddress) throws IOException {
        symbolTable.clear();
        pendingPatches.clear();
        buffer.reset();
        debugMap.clear();
        currentAddress = baseAddress;  // Считаем, что код начинается с 0 (или boot offset)

        String[] lines = source.split("\n");

        // --- ПРОХОД 1: Генерация кода и сбор меток ---
        for (String line : lines) {
            String originalLine = line.trim(); // Сохраняем для дебага до очистки
            line = originalLine;

            // Удаляем комментарии
            int commentIndex = line.indexOf(';');
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex).trim();
            }
            // Удаляем C-style комментарии //
            int doubleSlash = line.indexOf("//");
            if (doubleSlash >= 0) {
                line = line.substring(0, doubleSlash).trim();
            }

            if (line.isEmpty()) continue;

            if (line.startsWith("const ")) {
                parseConstant(line);
                continue;
            }
            // Обработка меток (например "start:")
            if (line.contains(":")) {
                int colonIdx = line.indexOf(':');
                String labelName = line.substring(0, colonIdx).trim();

                if (symbolTable.containsKey(labelName)) {
                    throw new IllegalArgumentException("Duplicate label definition: " + labelName);
                }

                symbolTable.put(labelName, currentAddress);

                // Отрезаем метку и работаем с остатком строки
                line = line.substring(colonIdx + 1).trim();

                // Если после метки ничего нет (пусто), переходим к следующей строке
                if (line.isEmpty()) continue;
            }
            debugMap.add(String.format("0x%04X : %s", currentAddress, line));
            // Обработка директив
            if (line.startsWith(".")) {
                parseDirective(line);
                continue;
            }

            if (line.startsWith("data ")) {
                parseDirective("." + line);
                continue;
            }
            // Парсинг инструкции
            parseInstruction(line);
        }

        byte[] binary = buffer.toByteArray();

        // --- ПРОХОД 2: Патчинг меток ---
        for (PendingPatch patch : pendingPatches) {
            if (!symbolTable.containsKey(patch.labelName)) {
                throw new IllegalArgumentException("Unknown label or symbol: " + patch.labelName);
            }

            int value = symbolTable.get(patch.labelName);
            int offset = patch.instructionAddress - baseAddress;

            // Инструкция: [Op:8][Rd:4][Rs:4][Imm:16]
            // Imm занимает последние 2 байта (offset + 2, offset + 3)

            // Простая проверка на переполнение для JMP/CALL (если адрес > 65535)
            // В реальном CPU для дальних переходов нужно использовать регистры,
            // но пока патчим младшие 16 бит.

            binary[offset] = (byte) (value & 0xFF);         // Младший байт Imm
            binary[offset + 1] = (byte) ((value >> 8) & 0xFF); // Старший байт Imm
        }

        return binary;
    }
    public List<String> getDebugMap() {
        return debugMap;
    }
    private void parseConstant(String line) {
        // const NAME = VALUE
        String content = line.substring(6).trim(); // убираем "const "
        String[] parts = content.split("=");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid constant format: " + line);
        }
        String name = parts[0].trim();
        String valueStr = parts[1].trim();
        try {
            int value = parseNumber(valueStr);
            symbolTable.put(name, value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid constant value for " + name + ": " + valueStr);
        }
    }

    private void parseDirective(String line) throws IOException {
        // Делим на саму директиву и всё остальное
        String[] parts = line.split("\\s+", 2);
        String directive = parts[0];

        if (directive.equals(".data") || directive.equals(".word") || directive.equals(".const")) {
            if (parts.length < 2) return;

            // Разделяем аргументы по запятой (для списков типа 0x80, 0x80...)
            String[] values = parts[1].split(",");
            for (String valStr : values) {
                int value = parseNumber(valStr.trim());

                // Пишем 4 байта (Little-Endian)
                buffer.write(value & 0xFF);
                buffer.write((value >>> 8) & 0xFF);
                buffer.write((value >>> 16) & 0xFF);
                buffer.write((value >>> 24) & 0xFF);
                currentAddress += 4;
            }
        }
    }

    private void parseInstruction(String line) throws IOException {
        String mnemonic;
        String argsRaw = "";

        int spaceIdx = line.indexOf(' ');
        if (spaceIdx == -1) {
            mnemonic = line.toLowerCase();
        } else {
            mnemonic = line.substring(0, spaceIdx).toLowerCase();
            argsRaw = line.substring(spaceIdx).trim();
        }

        if (!OPCODES.containsKey(mnemonic)) {
            throw new IllegalArgumentException("Unknown instruction: " + mnemonic);
        }

        int opcode = OPCODES.get(mnemonic);
        int rD = 0;
        int rS = 0;
        int imm = 0;
        boolean hasPatch = false;
        String patchLabel = null;

        if (!argsRaw.isEmpty()) {
            String[] args = splitArgs(argsRaw);

            // 1. Двухаргументные: Регистр-Регистр или Регистр-Число
            if (isRegImm(mnemonic) || isRegReg(mnemonic)) {
                if (args.length > 0) rD = parseRegister(args[0]);
                if (args.length > 1) {
                    try {
                        rS = parseRegister(args[1]);
                        if (mnemonic.endsWith("i")) {
                            opcode = OPCODES.get(mnemonic.substring(0, mnemonic.length() - 1));
                        }
                    } catch (Exception e) {
                        try {
                            imm = parseNumber(args[1]);
                        } catch (NumberFormatException nfe) {
                            hasPatch = true;
                            patchLabel = args[1];
                        }
                    }
                }
            }
            // 2. Одноаргументные (PUSH R1, WAIT R0, WAIT 100)
            else if (isSingleReg(mnemonic)) {
                // PUSH и OUT используют rS (источник)
                if (mnemonic.equals("push") || mnemonic.equals("out")) {
                    if (args.length > 0) rS = parseRegister(args[0]);
                    if (mnemonic.equals("out") && args.length > 1) {
                        // OUT port, Reg
                        try { imm = parseNumber(args[0]); } catch(Exception e) {hasPatch=true; patchLabel=args[0];}
                        rS = parseRegister(args[1]);
                    }
                } else {
                    // POP, WAIT, INC, DEC и т.д.
                    // Пытаемся распарсить аргумент как РЕГИСТР (rD)
                    try {
                        if (args.length > 0) rD = parseRegister(args[0]);
                    } catch (IllegalArgumentException e) {
                        // Если не вышло (например WAIT 100), пробуем как ЧИСЛО (Immediate)
                        // Это критическое исправление для WAIT
                        try {
                            imm = parseNumber(args[0]);
                        } catch (NumberFormatException nfe) {
                            hasPatch = true;
                            patchLabel = args[0];
                        }
                    }
                }
            }
            // 3. Переходы (JMP label) - только метки/числа
            else if (isJump(mnemonic)) {
                if (args.length > 0) {
                    try {
                        imm = parseNumber(args[0]);
                    } catch (NumberFormatException e) {
                        hasPatch = true;
                        patchLabel = args[0];
                    }
                }
            }
            // 4. Смещения (LDO R1, R2, 4)
            else if (isOffsetOp(mnemonic)) {
                if (args.length < 3) throw new IllegalArgumentException("LDO/STO require 3 args");
                rD = parseRegister(args[0]);
                rS = parseRegister(args[1]);
                try { imm = parseNumber(args[2]); }
                catch (NumberFormatException nfe) { hasPatch = true; patchLabel = args[2]; }
            }
            // 5. ST / LD
            else if (mnemonic.equals("ld")) {
                if (args.length > 0) rD = parseRegister(args[0]);
                if (args.length > 1) rS = parseRegister(args[1]);
            }
            else if (mnemonic.equals("st")) {
                if (args.length > 0) rD = parseRegister(args[0]);
                if (args.length > 1) rS = parseRegister(args[1]);
            }
        }

        int instruction = (opcode << 24) | ((rD & 0xF) << 20) | ((rS & 0xF) << 16) | (imm & 0xFFFF);

        if (hasPatch) {
            pendingPatches.add(new PendingPatch(currentAddress, patchLabel));
        }

        buffer.write(instruction & 0xFF);
        buffer.write((instruction >>> 8) & 0xFF);
        buffer.write((instruction >>> 16) & 0xFF);
        buffer.write((instruction >>> 24) & 0xFF);
        currentAddress += 4;
    }


    // Хелперы для определения типа аргументов
    private boolean isRegReg(String m) {
        return m.equals("mov") || m.equals("add") || m.equals("sub") || m.equals("mul") ||
                m.equals("div") || m.equals("mod") || m.equals("and") || m.equals("or") ||
                m.equals("xor") || m.equals("cmp") || m.equals("not") || m.equals("shl") ||
                m.equals("shr") || m.equals("addi") || m.equals("subi") || m.equals("andi") ||
                m.equals("ori");
    }
    private boolean isRegImm(String m) {
        return m.equals("ldi") || m.equals("lui") || m.equals("addi") || m.equals("subi") ||
                m.equals("cmpi") || m.equals("shl") || m.equals("shr") || m.equals("andi") || m.equals("ori");
    }
    private boolean isSingleReg(String m) {
        // ДОБАВЛЕНО: wait
        return m.equals("push") || m.equals("pop") || m.equals("get_temp") ||
                m.equals("get_clock") || m.equals("inc") || m.equals("dec") ||
                m.equals("jmpr") || m.equals("wait");
    }
    private boolean isJump(String m) {
        // УБРАНО: wait
        return m.equals("jmp") || m.equals("jz") || m.equals("jnz") || m.equals("jg") ||
                m.equals("jl") || m.equals("call");
    }

    private String[] splitArgs(String argsRaw) {
        String[] parts = argsRaw.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private int parseRegister(String token) {
        token = token.toUpperCase();
        if (token.startsWith("R")) {
            try {
                int reg = Integer.parseInt(token.substring(1));
                if (reg >= 0 && reg <= 15) return reg;
            } catch (NumberFormatException ignored) {}
        }
        if (token.equals("SP")) return 15;
        if (token.equals("PC")) return 15; // в архитектуре Lora SP часто R15

        throw new IllegalArgumentException("Invalid register: " + token);
    }

    public int parseNumber(String token) throws NumberFormatException {
        token = token.trim();
        if (token.startsWith("0x") || token.startsWith("0X")) {
            return (int) Long.parseLong(token.substring(2), 16);
        }
        return (int) Long.parseLong(token);
    }

    private boolean isOffsetOp(String m) {
        return m.equals("ldo") || m.equals("sto");
    }
}