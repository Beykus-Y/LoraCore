package com.loracore.lang.compiler;

public class ExpressionParser {
    private final CompilerContext context;
    private String exprSource;
    private int exprPos;

    public ExpressionParser(CompilerContext context) {
        this.context = context;
    }

    public void compile(String expr) {
        this.exprSource = expr;
        this.exprPos = 0;
        parseLogicalOr(); // Начало цепочки (самый низкий приоритет)
    }

    public void compile(String expr, int targetReg) {
        compile(expr);
        if (targetReg != 0) {
            context.emitter.emit("MOV", "R" + targetReg, "R0");
        }
    }

    // --- 1. Logical OR (||) ---
    // Short-circuit: если левая часть True (1), результат сразу 1, правая не вычисляется.
    private void parseLogicalOr() {
        parseLogicalAnd();

        while (checkNext('|') && checkNextMore('|')) { // ||
            skipWhitespace();
            exprPos += 2;
            String endLabel = context.nextLabel("logic_end");
            String trueLabel = context.nextLabel("logic_true");

            // R0 уже содержит результат левой части
            context.emitter.emit("CMPI", "R0", "0"); // Проверяем R0
            context.emitter.emitJump("JNZ", trueLabel); // Если R0 != 0 (True), прыгаем в true (short-circuit)

            // Если False, вычисляем правую часть
            parseLogicalAnd();
            context.emitter.emit("CMPI", "R0", "0");
            context.emitter.emitJump("JNZ", trueLabel);

            // Оба False
            context.emitter.emit("LDI", "R0", "0");
            context.emitter.emitJump("JMP", endLabel);

            context.emitter.emitLabel(trueLabel);
            context.emitter.emit("LDI", "R0", "1");

            context.emitter.emitLabel(endLabel);
        }
    }

    // --- 2. Logical AND (&&) ---
    // Short-circuit: если левая часть False (0), результат сразу 0.
    private void parseLogicalAnd() {
        parseBitwiseOr();

        while (checkNext('&') && checkNextMore('&')) { // &&
            skipWhitespace();
            exprPos += 2;
            String endLabel = context.nextLabel("logic_end");
            String falseLabel = context.nextLabel("logic_false");

            // R0 содержит левую часть
            context.emitter.emit("CMPI", "R0", "0");
            context.emitter.emitJump("JZ", falseLabel); // Если 0, сразу на выход (short-circuit)

            // Если True, вычисляем правую часть
            parseBitwiseOr();
            context.emitter.emit("CMPI", "R0", "0");
            context.emitter.emitJump("JZ", falseLabel);

            // Оба True
            context.emitter.emit("LDI", "R0", "1");
            context.emitter.emitJump("JMP", endLabel);

            context.emitter.emitLabel(falseLabel);
            context.emitter.emit("LDI", "R0", "0");

            context.emitter.emitLabel(endLabel);
        }
    }

    // --- 3. Bitwise OR (|) ---
    private void parseBitwiseOr() {
        parseBitwiseXor();
        while (checkNext('|') && !checkNextMore('|')) { // Одиночный |
            skipWhitespace();
            exprPos++;
            handleBinaryOp("OR");
        }
    }

    // --- 4. Bitwise XOR (^) ---
    private void parseBitwiseXor() {
        parseBitwiseAnd();
        while (checkNext('^')) {
            skipWhitespace();
            exprPos++;
            handleBinaryOp("XOR");
        }
    }

    // --- 5. Bitwise AND (&) ---
    private void parseBitwiseAnd() {
        parseEquality();
        while (checkNext('&') && !checkNextMore('&')) { // Одиночный &
            skipWhitespace();
            exprPos++;
            handleBinaryOp("AND");
        }
    }

    // --- 6. Equality (==, !=) ---
    private void parseEquality() {
        parseRelational();
        while (exprPos + 1 < exprSource.length()) {
            skipWhitespace();
            char c1 = exprSource.charAt(exprPos);
            char c2 = exprSource.charAt(exprPos + 1);
            if ((c1 == '=' || c1 == '!') && c2 == '=') {
                exprPos += 2;
                handleComparison(c1 == '!' ? "NEQ" : "EQ");
            } else {
                break;
            }
        }
    }

    // --- 7. Relational (<, >, <=, >=) ---
    private void parseRelational() {
        parseShift();
        while (exprPos < exprSource.length()) {
            skipWhitespace();
            char c = exprSource.charAt(exprPos);
            if (c == '<' || c == '>') {
                // Проверка на << и >> (это сдвиги, не сравнения)
                if (checkNextMore(c)) break; // Это сдвиг, выходим

                boolean eq = checkNextMore('=');
                exprPos += eq ? 2 : 1;

                String op = "";
                if (c == '<') op = eq ? "LE" : "LT";
                else op = eq ? "GE" : "GT";

                handleComparison(op);
            } else {
                break;
            }
        }
    }

    // --- 8. Bitwise Shift (<<, >>) ---
    private void parseShift() {
        parseAddSub();
        while (exprPos + 1 < exprSource.length()) {
            skipWhitespace();
            char c = exprSource.charAt(exprPos);
            if ((c == '<' || c == '>') && checkNextMore(c)) {
                exprPos += 2;
                if (c == '<') handleBinaryOp("SHL");
                else handleBinaryOp("SHR");
            } else {
                break;
            }
        }
    }

    // --- 9. Add/Sub (+, -) ---
    private void parseAddSub() {
        parseTerm();
        while (exprPos < exprSource.length()) {
            char op = exprSource.charAt(exprPos);
            if (op != '+' && op != '-') break;
            exprPos++;
            handleBinaryOp(op == '+' ? "ADD" : "SUB");
        }
    }

    // --- 10. Mul/Div/Mod (*, /, %) ---
    private void parseTerm() {
        parseFactor();
        while (exprPos < exprSource.length()) {
            skipWhitespace();
            char op = exprSource.charAt(exprPos);
            if (op != '*' && op != '/' && op != '%') break;
            if (op == '*' && checkNextMore('*')) break; // Skip **

            exprPos++;
            String asmOp = (op == '*') ? "MUL" : (op == '/') ? "DIV" : "MOD";
            handleBinaryOp(asmOp);
        }
    }

    // --- 11. Power (**) ---
    private void parseFactor() {
        skipWhitespace();
        parseUnary(); // Здесь вызываем унарные операции
        if (checkNext('*') && checkNextMore('*')) {
            exprPos += 2;
            context.emitter.emit("PUSH", "R0");
            parseFactor(); // Правая ассоциативность
            context.emitter.emit("MOV", "R1", "R0");
            context.emitter.emit("POP", "R0");
            context.emitter.emit("POW", "R0", "R1");
        }
    }

    // --- 12. Unary Operators (!, ~, -) ---
    private void parseUnary() {
        if (exprPos >= exprSource.length()) return;
        char c = exprSource.charAt(exprPos);

        if (c == '!' || c == '~' || c == '-') {
            // Проверка: это точно унарный минус, а не часть числа?
            // Если следующий символ цифра, parseNumber разберется сам, НО
            // -a, -(1+2) — это унарные минусы.
            // Простейшая эвристика: если это '-', проверяем дальше.
            // Но parseNumber сам съедает минус. Оставим минус для parsePrimary/Number
            // А вот ! и ~ точно наши.
            if (c == '-') {
                // Если далее цифра — это число, идем в primary
                if (exprPos + 1 < exprSource.length() && Character.isDigit(exprSource.charAt(exprPos + 1))) {
                    parsePrimary();
                    return;
                }
                // Иначе это унарный минус (инверсия знака)
                exprPos++;
                parseUnary(); // Рекурсия для - -x
                // R0 = -R0 <=> R0 = 0 - R0
                context.emitter.emit("MOV", "R1", "R0");
                context.emitter.emit("LDI", "R0", "0");
                context.emitter.emit("SUB", "R0", "R1");
                return;
            }

            exprPos++;
            parseUnary(); // Рекурсия (!!x)

            if (c == '~') {
                context.emitter.emit("NOT", "R0"); // Битовое НЕ
            } else if (c == '!') {
                // Логическое НЕ: 0->1, остальное->0
                String isZero = context.nextLabel("is_zero");
                String end = context.nextLabel("not_end");
                context.emitter.emit("CMPI", "R0", "0");
                context.emitter.emitJump("JZ", isZero);
                context.emitter.emit("LDI", "R0", "0");
                context.emitter.emitJump("JMP", end);
                context.emitter.emitLabel(isZero);
                context.emitter.emit("LDI", "R0", "1");
                context.emitter.emitLabel(end);
            }
            return;
        }
        parsePrimary();
    }

    // --- 13. Primary ---
    private void parsePrimary() {
        if (exprPos >= exprSource.length()) return;
        char c = exprSource.charAt(exprPos);

        if (c == '(') {
            exprPos++;
            parseLogicalOr();
            skipWhitespace(); // Пропускаем пробелы перед закрывающей скобкой
            if (exprPos < exprSource.length() && exprSource.charAt(exprPos) == ')') {
                exprPos++;
            }
            return;
        }

        if (c == '"') {
            int start = ++exprPos;
            StringBuilder sb = new StringBuilder();
            while (exprPos < exprSource.length()) {
                char ch = exprSource.charAt(exprPos);
                if (ch == '"') {
                    break;
                }
                sb.append(ch);
                exprPos++;
            }
            if (exprPos >= exprSource.length()) {
                throw new IllegalArgumentException("Unclosed string literal");
            }
            exprPos++;
            String content = sb.toString();
            String label = context.registerStringLiteral(content);
            context.emitter.emit("LDI", "R0", label);
            return;
        }


        // Обработка pop() (если добавляли ранее)
        if (exprSource.startsWith("pop()", exprPos)) {
            exprPos += 5;
            context.emitter.emit("POP", "R0");
            return;
        }

        if (Character.isDigit(c) || c == '-') {
            parseNumber(0);
            return;
        }

        if (Character.isLetter(c)) {
            if (exprSource.startsWith("mem[", exprPos)) {
                parseMemRead();
                return;
            }

            int start = exprPos;
            // Сканируем само имя (идентификатор)
            while (exprPos < exprSource.length() && (Character.isLetterOrDigit(exprSource.charAt(exprPos)) || exprSource.charAt(exprPos) == '_')) {
                exprPos++;
            }
            String name = exprSource.substring(start, exprPos);

            skipWhitespace();

            if (exprPos < exprSource.length() && exprSource.charAt(exprPos) == '(') {
                // Возвращаем позицию к началу имени, чтобы parseFunctionCall отработал корректно
                exprPos = start;
                parseFunctionCall();
            } else {
                // Если скобки нет — это переменная или константа
                exprPos = start; // Возвращаемся назад
                parseVariable(0);
            }
            // ----------------------------------------


        }
    }
    private void parseFunctionCall() {
        int start = exprPos;
        while (exprPos < exprSource.length() && (Character.isLetterOrDigit(exprSource.charAt(exprPos)) || exprSource.charAt(exprPos) == '_')) {
            exprPos++;
        }
        String name = exprSource.substring(start, exprPos);

        if (exprPos < exprSource.length() && exprSource.charAt(exprPos) == '(') {
            exprPos++; // Пропускаем '('
        }

        java.util.List<String> args = new java.util.ArrayList<>();
        int balance = 1;
        int argStart = exprPos;

        // Парсим аргументы внутри скобок
        while (exprPos < exprSource.length() && balance > 0) {
            char c = exprSource.charAt(exprPos);
            if (c == '(') balance++;
            if (c == ')') balance--;

            if (balance == 0) break; // Нашли закрывающую скобку функции

            if (c == ',' && balance == 1) {
                args.add(exprSource.substring(argStart, exprPos));
                argStart = exprPos + 1;
            }
            exprPos++;
        }

        // Добавляем последний аргумент
        if (argStart < exprPos) {
            String lastArg = exprSource.substring(argStart, exprPos).trim();
            if (!lastArg.isEmpty()) args.add(lastArg);
        }

        if (exprPos < exprSource.length() && exprSource.charAt(exprPos) == ')') {
            exprPos++; // Пропускаем ')'
        }

        // Генерируем код вызова (аргументы в стек)
        for (String arg : args) {
            ExpressionParser argParser = new ExpressionParser(context);
            argParser.compile(arg);
            context.emitter.emit("PUSH", "R0");
        }

        // Перекладываем из стека в регистры R0, R1...
        for (int i = args.size() - 1; i >= 0; i--) {
            context.emitter.emit("POP", "R" + i);
        }

        context.emitter.emitJump("CALL", name);
        // Результат функции по традиции остается в R0
    }
    // --- Helpers ---

    /**
     * Генерирует код для бинарной операции с учетом оптимизации (Fast Path).
     * Работает для арифметики и битовых операций.
     */
    private void handleBinaryOp(String asmOp) {
        if (asmOp.equals("SHL") || asmOp.equals("SHR")) {
            // Мы не можем использовать R1 для сдвига в этой архитектуре
            // Нужно парсить число напрямую в инструкцию
            // Для простоты пока оставим старую логику, но исправим Эмиттер
        }
        if (tryParseSimpleOperand(1)) {
            context.emitter.emit(asmOp, "R0", "R1");
        } else {
            context.emitter.emit("PUSH", "R0");
            // Вызываем метод следующего уровня приоритета, чтобы не зациклиться
            // Для простоты здесь вызываем parsePrimary или соответствующий уровень.
            // Но в нашей структуре "вниз" идти сложно без явного аргумента.
            // Поэтому проще вызвать parseUnary() или parsePrimary(), но это сломает приоритет (3 * 2 + 1).
            // В данной архитектуре (метод вызывает следующий), внутри цикла while мы ожидаем operand следующего уровня.
            // Пример: parseAddSub вызывает parseTerm.

            // Фикс: здесь нужно вызывать метод ТОГО ЖЕ уровня или выше.
            // Так как мы внутри цикла, мы уже распарсили левую часть.
            // Нам нужно распарсить правую часть, которая имеет БОЛЕЕ ВЫСОКИЙ приоритет.

            // Определяем, какой метод вызывать:
            if (asmOp.equals("ADD") || asmOp.equals("SUB")) parseTerm();
            else if (asmOp.equals("MUL") || asmOp.equals("DIV") || asmOp.equals("MOD")) parseFactor();
            else if (asmOp.equals("OR")) parseBitwiseXor(); // | вызывает ^
            else if (asmOp.equals("XOR")) parseBitwiseAnd(); // ^ вызывает &
            else if (asmOp.equals("AND")) parseEquality();   // & вызывает ==
            else if (asmOp.equals("SHL") || asmOp.equals("SHR")) parseAddSub(); // << вызывает +
            else parseFactor(); // Fallback

            context.emitter.emit("MOV", "R1", "R0");
            context.emitter.emit("POP", "R0");
            context.emitter.emit(asmOp, "R0", "R1");
        }
    }

    /**
     * Генерирует код сравнения, возвращающий 0 или 1 в R0.
     */
    private void handleComparison(String type) {
        context.emitter.emit("PUSH", "R0");
        parseShift(); // След. уровень
        context.emitter.emit("MOV", "R1", "R0");
        context.emitter.emit("POP", "R0");

        context.emitter.emit("CMP", "R0", "R1");

        String trueLabel = context.nextLabel("cmp_true");
        String endLabel = context.nextLabel("cmp_end");

        switch (type) {
            case "EQ" -> context.emitter.emitJump("JZ", trueLabel);
            case "NEQ" -> context.emitter.emitJump("JNZ", trueLabel);
            case "LT" -> context.emitter.emitJump("JL", trueLabel);
            case "GT" -> context.emitter.emitJump("JG", trueLabel);
            case "LE" -> { context.emitter.emitJump("JL", trueLabel); context.emitter.emitJump("JZ", trueLabel); }
            case "GE" -> { context.emitter.emitJump("JG", trueLabel); context.emitter.emitJump("JZ", trueLabel); }
        }

        context.emitter.emit("LDI", "R0", "0");
        context.emitter.emitJump("JMP", endLabel);

        context.emitter.emitLabel(trueLabel);
        context.emitter.emit("LDI", "R0", "1");

        context.emitter.emitLabel(endLabel);
    }

    private boolean checkNext(char c) {
        skipWhitespace(); // Важно пропускать пробелы перед проверкой!
        return exprPos < exprSource.length() && exprSource.charAt(exprPos) == c;
    }

    private boolean checkNextMore(char c) {
        return exprPos + 1 < exprSource.length() && exprSource.charAt(exprPos + 1) == c;
    }
    private void skipWhitespace() {
        while (exprPos < exprSource.length() && Character.isWhitespace(exprSource.charAt(exprPos))) {
            exprPos++;
        }
    }

    // --- Fast Path Optimization ---
    private boolean tryParseSimpleOperand(int targetReg) {
        int savePos = exprPos;
        skipWhitespace();
        if (exprPos >= exprSource.length()) return false;

        char c = exprSource.charAt(exprPos);

        if (Character.isDigit(c) || c == '-') {
            parseNumber(targetReg);
            if (isNextHighPriority()) { exprPos = savePos; return false; }
            return true;
        }

        if (Character.isLetter(c)) {
            if (exprSource.startsWith("mem[", exprPos)) return false;
            parseVariable(targetReg);
            if (isNextHighPriority()) { exprPos = savePos; return false; }
            return true;
        }
        return false;
    }

    private boolean isNextHighPriority() {
        if (exprPos < exprSource.length()) {
            char c = exprSource.charAt(exprPos);
            // Если дальше идет оператор с высоким приоритетом (*, /, ^, &, скобки и т.д.),
            // то мы не можем просто взять число, нужно считать всё выражение целиком.
            return c == '*' || c == '/' || c == '%' || c == '^' || c == '&' || c == '<' || c == '>';
        }
        return false;
    }

    private void parseNumber(int reg) {
        skipWhitespace();
        int start = exprPos;
        if (exprSource.charAt(exprPos) == '-') exprPos++;
        while (exprPos < exprSource.length()) {
            char c = exprSource.charAt(exprPos);
            if (Character.isDigit(c) || c == 'x' || c == 'X' || (c >= 'A' && c <= 'F')) exprPos++;
            else break;
        }
        String numStr = exprSource.substring(start, exprPos);
        int val = parseNumString(numStr);

        if (val > 0xFFFF || val < -0x8000) {
            context.emitter.emit("LUI", "R" + reg, String.valueOf((val >>> 16) & 0xFFFF));
            int lower = val & 0xFFFF;
            if (lower != 0) context.emitter.emit("ORI", "R" + reg, String.valueOf(lower));
        } else {
            context.emitter.emit("LDI", "R" + reg, String.valueOf(val));
        }
    }

    private void parseVariable(int reg) {
        int start = exprPos;
        while (exprPos < exprSource.length() && (Character.isLetterOrDigit(exprSource.charAt(exprPos)) || exprSource.charAt(exprPos) == '_')) {
            exprPos++;
        }
        String name = exprSource.substring(start, exprPos);

        if (context.hasConstant(name)) {
            int val = parseNumString(context.getConstant(name));
            if (val > 0xFFFF || val < 0) {
                // Если число больше 16 бит, используем LUI + ORI
                context.emitter.emit("LUI", "R" + reg, String.valueOf((val >>> 16) & 0xFFFF));
                int lower = val & 0xFFFF;
                if (lower != 0) {
                    context.emitter.emit("ORI", "R" + reg, String.valueOf(lower));
                }
            } else {
                // Если маленькое — по-старому
                context.emitter.emit("LDI", "R" + reg, String.valueOf(val));
            }
        } else if (context.hasVariable(name)) {
            String label = context.getVariableLabel(name);
            int addrReg = (reg == 2) ? 3 : 2;
            context.emitter.emit("LDI", "R" + addrReg, label);
            context.emitter.emit("LD", "R" + reg, "R" + addrReg);
        } else {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
    }

    private void parseMemRead() {
        // 1. Защита: проверяем, что мы действительно стоим на "mem["
        if (!exprSource.startsWith("mem[", exprPos)) {
            throw new IllegalArgumentException("Ошибка парсинга: ожидалось 'mem[' в позиции " + exprPos);
        }

        exprPos += 4; // Пропускаем "mem["
        int innerStart = exprPos;
        int balance = 1;

        // 2. Ищем закрывающую скобку с учетом вложенности []
        while (exprPos < exprSource.length() && balance > 0) {
            char c = exprSource.charAt(exprPos);
            if (c == '[') balance++;
            else if (c == ']') balance--;

            if (balance > 0) exprPos++;
        }
        String innerExpr = exprSource.substring(innerStart, exprPos);

        // Защита: если строка кончилась, а скобка не найдена
        if (balance != 0) {
            throw new IllegalArgumentException("Ошибка: не найдена закрывающая скобка ']' для mem[]");
        }

        // Извлекаем выражение внутри скобок


        // Защита: mem[] — пустой адрес
        if (innerExpr.trim().isEmpty()) {
            throw new IllegalArgumentException("Ошибка: пустой адрес внутри mem[]");
        }

        exprPos++; // Пропускаем саму закрывающую скобку ']'

        // 3. Компилируем адрес (рекурсивно)
        // Используем НОВЫЙ экземпляр парсера, чтобы внутренний вызов compile()
        // не сбросил состояние (exprPos) текущего парсера.
        ExpressionParser addrParser = new ExpressionParser(context);
        addrParser.compile(innerExpr);
        // Результат вычисления адреса теперь лежит в R0

        // 4. Генерируем код чтения из памяти
        // Мы берем адрес из R0, перекладываем в R1 и читаем данные из этого адреса обратно в R0
        context.emitter.emit("MOV", "R1", "R0"); // Подготовка адреса в R1
        context.emitter.emit("LD", "R0", "R1");  // R0 = mem[R1]
    }

    private int parseNumString(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseUnsignedInt(s.substring(2), 16);
        return Integer.parseInt(s);
    }
}