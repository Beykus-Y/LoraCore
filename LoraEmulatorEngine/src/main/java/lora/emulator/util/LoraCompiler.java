package lora.emulator.util;

import java.util.*;
import java.util.regex.*;

/**
 * LoraCompiler v3.1 (Bitwise Fix Edition).
 *
 * Changes:
 * - Added support for binary &, |, ^ operators.
 * - Fixed parsing hierarchy.
 */
public class LoraCompiler {

    // --- State ---
    private final StringBuilder output = new StringBuilder();
    private final Map<String, Integer> locals = new HashMap<>();
    private final Map<String, Integer> args = new HashMap<>();
    private final Map<String, String> stringTable = new LinkedHashMap<>();

    private List<Token> tokens;
    private int pos;
    private int labelCounter = 0;
    private int stringCounter = 0;
    private int currentStackOffset = 0;

    // --- Public API ---

    public String compile(String source) {
        output.setLength(0);
        stringTable.clear();
        labelCounter = 0;
        stringCounter = 0;

        source = source.replace("\uFEFF", "").replace("\r", "");

        emit("; === LORA COMPILER v3.1 GENERATED CODE ===");
        emit(".ORG 0x0500");
        emit("LDI R15, 0x1FFC ; Init SP");
        emit("CALL _main");
        emit("HLT ; Stop if main returns");
        emit("");

        tokens = tokenize(source);
        pos = 0;

        while (!isAtEnd()) {
            if (match("func")) {
                parseFunction();
            } else if (match("asm")) {
                parseAsmBlock();
            } else {
                String val = peek().value;
                String debugVal = val.isBlank() ? String.format("\\u%04x", (int)val.charAt(0)) : val;
                if (val.isEmpty()) debugVal = "EOF";
                error("Unexpected token at global scope: '" + val + "' (Debug: " + debugVal + ")");
            }
        }

        if (!stringTable.isEmpty()) {
            emit("");
            emit("; --- READ ONLY DATA ---");
            for (Map.Entry<String, String> entry : stringTable.entrySet()) {
                emit(entry.getValue() + ": .ASCII " + entry.getKey());
            }
        }

        return output.toString();
    }

    // --- Parser: Top Level ---

    private void parseFunction() {
        String name = consume().value;
        consume("(");

        locals.clear();
        args.clear();
        currentStackOffset = 0;

        List<String> argNames = new ArrayList<>();
        if (!check(")")) {
            do {
                argNames.add(consume().value);
            } while (match(","));
        }
        consume(")");
        consume("{");

        for (int i = 0; i < argNames.size(); i++) {
            int offset = 4 + (argNames.size() - 1 - i) * 4;
            args.put(argNames.get(i), offset);
        }

        emit("_" + name + ":");

        while (!check("}") && !isAtEnd()) {
            parseStatement();
        }
        consume("}");

        if (currentStackOffset > 0) {
            emit("ADDI R15, " + currentStackOffset + " ; Clean locals (implicit)");
        }
        emit("RET");
        emit("");
    }

    // --- Parser: Statements ---

    private void parseStatement() {
        if (match("var")) {
            parseVarDecl();
        } else if (match("if")) {
            parseIf();
        } else if (match("while")) {
            parseWhile();
        } else if (match("return")) {
            parseReturn();
        } else if (match("asm")) {
            parseAsmBlock();
        } else if (match("{")) {
            while (!check("}")) parseStatement();
            consume("}");
        } else if (check("*")) {
            consume("*");
            parseUnary(); // Address

            if (match("=")) {
                emit("PUSH R0");
                parseExpression(); // Value
                emit("POP R1");
                emit("ST R1, R0");
                consume(";");
            } else {
                emit("LD R0, R0");
                consume(";");
            }
        } else {
            parseExpression();
            consume(";");
        }
    }

    private void parseVarDecl() {
        String name = consume().value;
        consume("=");
        parseExpression();
        consume(";");

        emit("PUSH R0 ; Var " + name);
        currentStackOffset += 4;
        locals.put(name, currentStackOffset);
    }

    private void parseIf() {
        consume("(");
        parseExpression();
        consume(")");

        int labelElse = labelCounter++;
        int labelEnd = labelCounter++;

        emit("CMPI R0, 0");
        emit("JZ _L" + labelElse);

        parseStatement();

        emit("JMP _L" + labelEnd);
        emit("_L" + labelElse + ":");

        if (match("else")) {
            parseStatement();
        }

        emit("_L" + labelEnd + ":");
    }

    private void parseWhile() {
        int labelStart = labelCounter++;
        int labelEnd = labelCounter++;

        emit("_L" + labelStart + ":");
        consume("(");
        parseExpression();
        consume(")");

        emit("CMPI R0, 0");
        emit("JZ _L" + labelEnd);

        parseStatement();

        emit("JMP _L" + labelStart);
        emit("_L" + labelEnd + ":");
    }

    private void parseReturn() {
        if (!check(";")) {
            parseExpression();
        }
        consume(";");

        if (currentStackOffset > 0) {
            emit("ADDI R15, " + currentStackOffset + " ; Clean locals (return)");
        }
        emit("RET");
    }

    private void parseAsmBlock() {
        consume("{");
        StringBuilder sb = new StringBuilder();
        while (!check("}")) {
            Token t = consume();
            if (t.value.equals(";")) {
                emit(sb.toString());
                sb.setLength(0);
            } else if (t.value.equals(":")) {
                sb.append(":");
                emit(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(t.value).append(" ");
            }
        }
        if (sb.length() > 0) emit(sb.toString());
        consume("}");
    }

    // --- Parser: Expression Hierarchy ---

    // 1. Assignment
    private void parseExpression() {
        int startPos = pos;
        if (tokens.get(pos).type == TokenType.ID && tokens.get(pos+1).value.equals("=")) {
            String name = consume().value;
            consume("=");
            parseExpression();
            emitStoreToVar(name);
            return;
        }

        if (match("*")) {
            pos = startPos;
        }

        parseLogicOr();
    }

    // 2. Logic OR (||)
    private void parseLogicOr() {
        parseLogicAnd();
        while (match("||")) {
            emit("PUSH R0");
            parseLogicAnd();
            emit("POP R1");
            emit("OR R0, R1");
        }
    }

    // 3. Logic AND (&&)
    private void parseLogicAnd() {
        parseEquality();
        while (match("&&")) {
            emit("PUSH R0");
            parseEquality();
            emit("POP R1");
            emit("AND R0, R1");
        }
    }

    // 4. Equality (==, !=)
    private void parseEquality() {
        parseRelational();
        while (check("==") || check("!=")) {
            String op = consume().value;
            emit("PUSH R0");
            parseRelational();
            emit("POP R1");

            emit("CMP R1, R0");
            int lTrue = labelCounter++;
            int lEnd = labelCounter++;

            if (op.equals("==")) emit("JZ _L" + lTrue);
            else emit("JNZ _L" + lTrue);

            emit("LDI R0, 0");
            emit("JMP _L" + lEnd);
            emit("_L" + lTrue + ":");
            emit("LDI R0, 1");
            emit("_L" + lEnd + ":");
        }
    }

    // 5. Relational (<, >, <=, >=)
    private void parseRelational() {
        parseBitwise(); // <--- ЛИНУС: Теперь зовем Bitwise вместо AddSub
        while (check("<") || check(">") || check("<=") || check(">=")) {
            String op = consume().value;
            emit("PUSH R0");
            parseBitwise();
            emit("POP R1");

            emit("CMP R1, R0");
            int lTrue = labelCounter++;
            int lEnd = labelCounter++;

            switch (op) {
                case "<" -> emit("JL _L" + lTrue);
                case ">" -> emit("JG _L" + lTrue);
                case "<=" -> { emit("JL _L" + lTrue); emit("JZ _L" + lTrue); }
                case ">=" -> { emit("JG _L" + lTrue); emit("JZ _L" + lTrue); }
            }

            emit("LDI R0, 0");
            emit("JMP _L" + lEnd);
            emit("_L" + lTrue + ":");
            emit("LDI R0, 1");
            emit("_L" + lEnd + ":");
        }
    }

    // 5.5. Bitwise (&, |, ^) - НОВОЕ!
    private void parseBitwise() {
        parseAddSub();
        while (check("&") || check("|") || check("^")) {
            String op = consume().value;
            emit("PUSH R0");
            parseAddSub();
            emit("POP R1");
            // R1 = LHS, R0 = RHS
            if (op.equals("&")) emit("AND R1, R0");
            else if (op.equals("|")) emit("OR R1, R0");
            else if (op.equals("^")) emit("XOR R1, R0");

            emit("MOV R0, R1");
        }
    }

    // 6. Additive (+, -)
    private void parseAddSub() {
        parseMulDiv();
        while (check("+") || check("-")) {
            String op = consume().value;
            emit("PUSH R0");
            parseMulDiv();
            emit("POP R1");
            if (op.equals("+")) {
                emit("ADD R0, R1");
            } else {
                emit("SUB R1, R0");
                emit("MOV R0, R1");
            }
        }
    }

    // 7. Multiplicative (*, /, %)
    private void parseMulDiv() {
        parseUnary();
        while (check("*") || check("/") || check("%")) {
            String op = consume().value;
            emit("PUSH R0");
            parseUnary();
            emit("POP R1");
            if (op.equals("*")) {
                emit("MUL R0, R1");
            } else if (op.equals("/")) {
                emit("DIV R1, R0");
                emit("MOV R0, R1");
            } else {
                emit("MOD R1, R0");
                emit("MOV R0, R1");
            }
        }
    }

    // 8. Unary (-, !, *, &)
    private void parseUnary() {
        if (match("-")) {
            parseUnary();
            emit("MOV R1, R0");
            emit("LDI R0, 0");
            emit("SUB R0, R1");
        }
        else if (match("!")) {
            parseUnary();
            emit("NOT R0");
        }
        else if (match("&")) {
            // Address Of variable
            String name = consume().value;
            if (locals.containsKey(name)) {
                int depth = locals.get(name);
                int offset = (currentStackOffset - depth);
                emit("MOV R0, R15");
                if (offset > 0) emit("ADDI R0, " + offset);
            } else if (args.containsKey(name)) {
                int baseOff = args.get(name);
                emit("MOV R0, R15");
                emit("ADDI R0, " + (currentStackOffset + baseOff));
            } else {
                error("Cannot take address of unknown '" + name + "'");
            }
        }
        else if (match("*")) {
            parseUnary();
            emit("LD R0, R0");
        }
        else {
            parsePrimary();
        }
    }

    // 9. Primary
    private void parsePrimary() {
        if (match("(")) {
            parseExpression();
            consume(")");
        }
        else if (checkNumber()) {
            String num = consume().value;
            long val = parseLiteral(num);
            loadImm(val);
        }
        else if (checkString()) {
            String strLiteral = consume().value;
            String content = strLiteral.substring(1, strLiteral.length()-1);
            if (!stringTable.containsKey(content)) {
                stringTable.put(content, "L_STR_" + stringCounter++);
            }
            String label = stringTable.get(content);
            emit("LDI R0, " + label);
        }
        else if (checkID()) {
            String name = consume().value;
            if (match("(")) {
                parseCall(name);
            } else {
                emitLoadVar(name);
            }
        }
        else {
            error("Expected expression, found: " + peek().value);
        }
    }

    // --- Helpers ---

    private void parseCall(String name) {
        int argCount = 0;
        if (!check(")")) {
            do {
                parseExpression();
                emit("PUSH R0");
                argCount++;
            } while (match(","));
        }
        consume(")");

        emit("CALL _" + name);
        if (argCount > 0) {
            emit("ADDI R15, " + (argCount * 4) + " ; Clean args");
        }
    }

    private void emitLoadVar(String name) {
        if (locals.containsKey(name)) {
            int depth = locals.get(name);
            int offset = currentStackOffset - depth;
            emit("MOV R1, R15");
            if (offset > 0) emit("ADDI R1, " + offset);
            emit("LD R0, R1");
        }
        else if (args.containsKey(name)) {
            int argOff = args.get(name);
            int totalOff = currentStackOffset + argOff;
            emit("MOV R1, R15");
            emit("ADDI R1, " + totalOff);
            emit("LD R0, R1");
        }
        else {
            error("Undefined variable: " + name);
        }
    }

    private void emitStoreToVar(String name) {
        if (locals.containsKey(name)) {
            int depth = locals.get(name);
            int offset = currentStackOffset - depth;
            emit("MOV R1, R15");
            if (offset > 0) emit("ADDI R1, " + offset);
            emit("ST R1, R0");
        }
        else if (args.containsKey(name)) {
            int argOff = args.get(name);
            int totalOff = currentStackOffset + argOff;
            emit("MOV R1, R15");
            emit("ADDI R1, " + totalOff);
            emit("ST R1, R0");
        }
        else {
            error("Undefined variable for assignment: " + name);
        }
    }

    private void loadImm(long val) {
        if (val >= 0 && val <= 0xFFFF) {
            emit("LDI R0, " + val);
        } else {
            // Handle signed/large ints properly
            int ival = (int) val;
            emit("LUI R0, " + ((ival >>> 16) & 0xFFFF));
            if ((ival & 0xFFFF) != 0) {
                emit("ORI R0, " + (ival & 0xFFFF));
            }
        }
    }

    private void emit(String s) {
        output.append(s).append("\n");
    }

    // --- Tokenizer ---

    private enum TokenType { ID, NUM, STR, OP, EOF }
    private record Token(TokenType type, String value, int line) {}

    private List<Token> tokenize(String src) {
        List<Token> ts = new ArrayList<>();
        // RegEx Updated: Added explicit support for single char ops | & ^
        String pattern = "//.*|0x[0-9a-fA-F]+|\\d+|\"[^\"]*\"|==|!=|<=|>=|\\|\\||&&|[a-zA-Z_]\\w*|.";
        Matcher m = Pattern.compile(pattern).matcher(src);

        int line = 1;
        while (m.find()) {
            String s = m.group();
            if (s.startsWith("//")) continue;
            if (s.isBlank()) {
                if (s.contains("\n")) line++;
                continue;
            }

            TokenType type;
            if (s.matches("0x.*|\\d+")) type = TokenType.NUM;
            else if (s.startsWith("\"")) type = TokenType.STR;
            else if (s.matches("[a-zA-Z_]\\w*")) {
                if (isKeyword(s)) type = TokenType.OP;
                else type = TokenType.ID;
            }
            else type = TokenType.OP;

            ts.add(new Token(type, s, line));
        }
        ts.add(new Token(TokenType.EOF, "", line));
        return ts;
    }

    private boolean isKeyword(String s) {
        return Set.of("if", "else", "while", "return", "var", "func", "asm").contains(s);
    }

    // --- Token Consumer Methods ---

    private Token peek() { return tokens.get(pos); }
    private boolean isAtEnd() { return peek().type == TokenType.EOF; }

    private Token consume() {
        if (!isAtEnd()) return tokens.get(pos++);
        return peek();
    }

    private boolean check(String val) {
        if (isAtEnd()) return false;
        return peek().value.equals(val);
    }

    private boolean match(String val) {
        if (check(val)) {
            pos++;
            return true;
        }
        return false;
    }

    private void consume(String val) {
        if (!match(val)) error("Expected '" + val + "' but found '" + peek().value + "'");
    }

    private boolean checkID() { return peek().type == TokenType.ID; }
    private boolean checkNumber() { return peek().type == TokenType.NUM; }
    private boolean checkString() { return peek().type == TokenType.STR; }

    private long parseLiteral(String s) {
        if (s.startsWith("0x")) return Long.parseUnsignedLong(s.substring(2), 16);
        return Long.parseLong(s);
    }

    private void error(String msg) {
        Token t = peek();
        throw new RuntimeException("[Compiler Error] Line " + t.line + ": " + msg);
    }
}