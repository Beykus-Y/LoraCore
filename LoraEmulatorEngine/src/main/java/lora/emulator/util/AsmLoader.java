package lora.emulator.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LORA Assembler Loader (Debug Edition)
 * Теперь он не молчит, как партизан.
 */
public final class AsmLoader {

    public static boolean DEBUG = true; // Выключишь, когда все заработает
    private static final int DEFAULT_BINARY_SIZE = 64 * 1024;

    /* ============================================================
       ПУБЛИЧНЫЙ API
       ============================================================ */

    public static byte[] loadFromResources(String fileName) {
        if (DEBUG) System.out.println("[ASM] Loading: " + fileName);
        try (InputStream is = AsmLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new FileNotFoundException("ASM file not found in resources: " + fileName);
            }

            List<String> source = readAllLines(is);
            if (DEBUG) System.out.println("[ASM] Source lines read: " + source.size());
            return assemble(source);

        } catch (Exception e) {
            // Линус: Теперь мы печатаем стектрейс, чтобы видеть ГДЕ упало
            e.printStackTrace();
            throw new RuntimeException("Assembler FAILED: " + e.getMessage(), e);
        }
    }

    /* ============================================================
       ОСНОВНОЙ АССЕМБЛЕР
       ============================================================ */

    public static byte[] assemble(List<String> source) {

        Map<String, Integer> labels = new HashMap<>();
        List<AsmLine> program = new ArrayList<>();

        int pc = 0;

        if (DEBUG) System.out.println("--- PASS 1: Labels & Addresses ---");

        /* ---------- PASS 1: сбор меток и адресов ---------- */
        for (int lineNum = 0; lineNum < source.size(); lineNum++) {
            String raw = source.get(lineNum);
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;

            // Метка
            if (line.endsWith(":")) {
                String label = line.substring(0, line.length() - 1).trim();
                validateLabel(label, lineNum);
                labels.put(label, pc);
                if (DEBUG) System.out.printf("  [L] %s -> 0x%04X\n", label, pc);
                continue;
            }

            String[] tokens = tokenize(line);
            if (tokens.length == 0) continue; // Пустая строка после стрипа
            String cmd = tokens[0].toUpperCase();

            AsmLine asm = new AsmLine(lineNum, pc, line, tokens);
            program.add(asm);

            if (cmd.equals(".ORG")) {
                int newAddr = parseLiteral(tokens[1]);
                if (newAddr < pc) {
                    throw asm.error(".ORG moves address backwards");
                }
                pc = newAddr;
                asm.size = 0;
                if (DEBUG) System.out.printf("  [DIR] .ORG 0x%04X\n", pc);
            }
            else if (cmd.equals(".DB")) {
                asm.size = tokens.length - 1;
                pc += asm.size;
            }
            else if (cmd.equals(".ASCII")) {
                String str = extractString(line);
                asm.size = str.length() + 1;
                pc += asm.size;
            }
            else if (cmd.startsWith(".DEF")) {
                // Поддержка констант вида .DEF NAME VALUE
                if (tokens.length < 3) throw asm.error(".DEF requires name and value");
                String name = tokens[1];
                int val = parseLiteral(tokens[2]);
                labels.put(name, val);
                asm.size = 0; // .DEF не занимает места в памяти
                if (DEBUG) System.out.printf("  [D] %s = 0x%X\n", name, val);
            }
            else {
                asm.size = 4;
                pc += 4;
            }
        }

        if (DEBUG) System.out.println("--- PASS 2: Code Generation ---");

        /* ---------- PASS 2: генерация байт-кода ---------- */
        int binarySize = Math.max(pc, DEFAULT_BINARY_SIZE);
        byte[] binary = new byte[binarySize];

        for (AsmLine asm : program) {
            // Пропускаем строки без размера (ORG, DEF)
            if (asm.size == 0) continue;

            String cmd = asm.tokens[0].toUpperCase();

            try {
                if (cmd.equals(".DB")) {
                    emitDB(binary, asm);
                }
                else if (cmd.equals(".ASCII")) {
                    emitASCII(binary, asm);
                }
                else {
                    emitInstruction(binary, asm, labels);
                }

            } catch (Exception e) {
                throw asm.error(e.getMessage());
            }
        }

        if (DEBUG) System.out.println("[ASM] Assembly Complete. Binary size: " + pc + " bytes.");
        return Arrays.copyOf(binary, pc);
    }

    /* ============================================================
       ГЕНЕРАЦИЯ ДАННЫХ
       ============================================================ */

    private static void emitDB(byte[] binary, AsmLine asm) {
        for (int i = 1; i < asm.tokens.length; i++) {
            byte val = (byte) parseLiteral(asm.tokens[i]);
            binary[asm.address + i - 1] = val;
        }
        if (DEBUG) System.out.printf("  %04X: [DATA] .DB ... (%d bytes)\n", asm.address, asm.size);
    }

    private static void emitASCII(byte[] binary, AsmLine asm) {
        String str = extractString(asm.rawLine);
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, binary, asm.address, bytes.length);
        binary[asm.address + bytes.length] = 0;
        if (DEBUG) System.out.printf("  %04X: [DATA] \"%s\"\n", asm.address, str);
    }

    /* ============================================================
       ГЕНЕРАЦИЯ ИНСТРУКЦИЙ
       ============================================================ */

    private static void emitInstruction(
            byte[] binary,
            AsmLine asm,
            Map<String, Integer> labels
    ) {
        String[] p = asm.tokens;
        String cmd = p[0].toUpperCase();

        int rD = 0;
        int rS = 0;
        int imm = 0;
        String finalCmd = cmd;

        // ЛОГИКА ПАРСИНГА АРГУМЕНТОВ
        if (cmd.startsWith("J") || cmd.equals("CALL")) {
            // JMP Label
            imm = resolveValue(p[1], labels);
        }
        else if (cmd.equals("PUSH") || cmd.equals("POP") || cmd.equals("NOT") || cmd.equals("GET_TEMP")) {
            // 1 аргумент
            if (isRegister(p[1])) rD = parseRegister(p[1]);
            else rD = 0; // Для PUSH src

            // Для PUSH нам нужно rSrc, а не rDest.
            // InstructionSet.asm("PUSH", rD, rS, imm);
            // OP_PUSH = 0x05. Формат: PUSH R_src.
            // Значит rDest не важен, rSrc = p[1].
            if (cmd.equals("PUSH") || cmd.equals("SET_VOLT")) {
                rS = parseRegister(p[1]);
                rD = 0;
            } else {
                rD = parseRegister(p[1]);
            }
        }
        else {
            // 2 или 3 аргумента
            // MOV R0, R1
            // LDI R0, 100

            if (p.length > 1) {
                if (isRegister(p[1])) rD = parseRegister(p[1]);
                else imm = resolveValue(p[1], labels);
            }

            if (p.length > 2) {
                if (isRegister(p[2])) {
                    rS = parseRegister(p[2]);
                } else {
                    imm = resolveValue(p[2], labels);
                    finalCmd = autoImmediate(cmd);
                }
            }
        }

        int instr = InstructionSet.asm(finalCmd, rD, rS, imm);
        writeLE32(binary, asm.address, instr);

        if (DEBUG) {
            System.out.printf("  %04X: %08X | %-25s (Op:%s Rd:%d Rs:%d Imm:%X)\n",
                    asm.address, instr, asm.rawLine, finalCmd, rD, rS, imm);
        }
    }

    /* ============================================================
       УТИЛИТЫ
       ============================================================ */

    private static void writeLE32(byte[] mem, int addr, int value) {
        mem[addr]     = (byte) (value);
        mem[addr + 1] = (byte) (value >> 8);
        mem[addr + 2] = (byte) (value >> 16);
        mem[addr + 3] = (byte) (value >> 24);
    }

    private static String autoImmediate(String cmd) {
        return switch (cmd) {
            case "MOV" -> "LDI";
            case "ADD" -> "ADDI";
            case "SUB" -> "SUBI";
            case "AND" -> "ANDI";
            case "CMP" -> "CMPI";
            default -> cmd;
        };
    }

    /* ============================================================
       ПАРСИНГ
       ============================================================ */

    private static List<String> readAllLines(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        List<String> out = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) out.add(line);
        return out;
    }

    private static String stripComment(String s) {
        int idx = s.indexOf(';');
        return idx == -1 ? s : s.substring(0, idx);
    }

    private static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        // Исправленный RegEx для токенизации (поддержка запятых)
        Matcher m = Pattern.compile("([^\",\\s]+|\".+?\")").matcher(line);
        while (m.find()) {
            String t = m.group(1).trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        return tokens.toArray(new String[0]);
    }

    private static String extractString(String line) {
        int s = line.indexOf('"');
        int e = line.lastIndexOf('"');
        if (s == -1 || e <= s) return "";
        return line.substring(s + 1, e);
    }

    private static boolean isRegister(String s) {
        return s.toUpperCase().matches("R\\d+");
    }

    private static int parseRegister(String s) {
        // Убираем 'R' или 'r' и запятые
        s = s.toUpperCase().replace(",", "").replace("R", "");
        int r = Integer.parseInt(s);
        if (r < 0 || r > 31)
            throw new IllegalArgumentException("Register out of range: " + s);
        return r;
    }

    private static int resolveValue(String s, Map<String, Integer> labels) {
        s = s.replace(",", ""); // Убираем запятую, если приклеилась
        if (labels.containsKey(s)) return labels.get(s);
        return parseLiteral(s);
    }

    private static int parseLiteral(String s) {
        s = s.replace(",", "");
        try {
            if (s.startsWith("0x") || s.startsWith("0X"))
                return Integer.parseUnsignedInt(s.substring(2), 16);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid literal or undefined label: " + s);
        }
    }

    private static void validateLabel(String s, int line) {
        if (!s.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new RuntimeException("Invalid label at line " + (line + 1) + ": " + s);
        }
    }

    /* ============================================================
       ВНУТРЕННИЕ СТРУКТУРЫ
       ============================================================ */

    private static final class AsmLine {
        final int lineNumber;
        final int address;
        final String rawLine;
        final String[] tokens;
        int size;

        AsmLine(int lineNumber, int address, String rawLine, String[] tokens) {
            this.lineNumber = lineNumber;
            this.address = address;
            this.rawLine = rawLine;
            this.tokens = tokens;
        }

        RuntimeException error(String msg) {
            return new RuntimeException(
                    "[ASM ERROR] line " + (lineNumber + 1) +
                            " @0x" + Integer.toHexString(address) +
                            " : " + msg + "\n> " + rawLine
            );
        }
    }
}