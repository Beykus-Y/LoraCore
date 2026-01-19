package com.loracore.lang.compiler;

import java.util.ArrayList;
import java.util.List;

public class StatementParser {
    private final CompilerContext context;
    private final ExpressionParser exprParser;

    public StatementParser(CompilerContext context) {
        this.context = context;
        this.exprParser = new ExpressionParser(context);
    }

    public void parseBlock(List<String> lines) {
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim(); // Убираем пробелы сразу

            // Игнорируем пустые строки и комментарии
            if (line.isEmpty() || line.startsWith("//")) {
                i++;
                continue;
            }

            if (line.equals("{") || line.equals("}")) {
                i++;
                continue;
            }
            try {
                // Используем вспомогательный метод для проверки ключевых слов (чтобы if_var не стал командой if)
                if (isCommand(line, "let")) {
                    handleLet(line);
                } else if (isCommand(line, "while")) {
                    i = handleWhile(lines, i);
                    continue; // Мы не делаем i++, так как handleWhile вернул нам индекс конца блока
                } else if (isCommand(line, "if")) {
                    i = handleIf(lines, i);
                    continue; // Аналогично
                } else if (isCommand(line, "return")) {
                    handleReturn(line);
                } else if (line.startsWith("mem[")) {
                    handleMemWrite(line);
                } else if (line.equals("break;")) {
                    String loopEnd = context.peekLoopEnd();
                    if (loopEnd != null) context.emitter.emitJump("JMP", loopEnd);
                } else if (line.equals("halt;")) {
                    context.emitter.emit("HLT");
                } else if (line.startsWith("wait(") && line.endsWith(");")) {
                    handleWait(line);
                } else if (line.contains("(") && line.endsWith(");")) {
                    handleCall(line);
                } else if (line.contains("=") && !line.contains("==")) {
                    handleAssignment(line);
                } else if (line.startsWith("push(")) {
                    handlePush(line);
                } else {
                    throw new IllegalArgumentException("Unknown command or syntax error");
                }
            } catch (LoraCompilerException lce) {
                throw lce; // Пробрасываем уже сформированную ошибку дальше
            } catch (Exception e) {
                // Оборачиваем любую другую ошибку, добавляя контекст строки
                // i + 1, так как строки начинаются с 0
                throw new LoraCompilerException(e.getMessage(), i + 1, line, e);
            }

            i++; // Переходим к следующей строке, если не было прыжка в handle-методах
        }
    }

    private void handleWait(String line) {
        // Формат: wait(10);
        int start = line.indexOf('(') + 1;
        int end = line.lastIndexOf(')');
        String arg = line.substring(start, end).trim();

        // Проверяем, является ли аргумент числом
        // Текущая архитектура VM (OP_WAIT) поддерживает только Immediate (константу)
        try {
            // Поддержка hex (0x10) и dec (16)
            int cycles;
            if (arg.startsWith("0x") || arg.startsWith("0X")) {
                cycles = Integer.parseInt(arg.substring(2), 16);
            } else {
                cycles = Integer.parseInt(arg);
            }
            context.emitter.emit("WAIT", String.valueOf(cycles));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Instruction 'wait' currently supports only constant integer arguments (e.g., wait(10)). Given: " + arg);
        }
    }

    // Вспомогательный метод для Dennis Ritchie-style парсинга
    private boolean isCommand(String line, String cmd) {
        String c = cmd.trim(); // Убираем лишние пробелы из искомой команды
        if (!line.startsWith(c)) return false;

        // Если строка состоит только из команды (например, "halt")
        if (line.length() == c.length()) return true;

        // Проверяем символ сразу после команды: это должен быть пробел, скобка или табуляция
        char next = line.charAt(c.length());
        return next == ' ' || next == '(' || next == '{' || next == '\t' || next == ';';
    }
    private void handlePush(String line) {
        String content = line.substring(5, line.lastIndexOf(')')).trim(); // убираем "push(" и ")"
        exprParser.compile(content); // Результат выражения попадает в R0
        context.emitter.emit("PUSH", "R0");
    }

    private void handleReturn(String line) {
        String content = line.trim().substring(6).replace(";", "").trim();

        // 2. Если есть выражение (например, return 1;), компилируем его
        if (!content.isEmpty()) {
            exprParser.compile(content);
            // Результат выражения автоматически окажется в R0
        }

        // 3. Прыгаем на метку выхода текущей функции
        String exitLabel = context.getCurrentFunctionExit();
        if (exitLabel != null) {
            context.emitter.emitJump("JMP", exitLabel);
        } else {
            // Защита: если return написан вне функции (в глобальной области)
            context.emitter.emit("RET");
        }
    }

    private void handleLet(String line) {
        String content = line.trim().replaceFirst("^let\\s+", "").replace(";", "");
        String[] parts = content.split("=", 2);

        if (parts.length < 2) throw new IllegalArgumentException("Invalid let statement: " + line);

        String name = parts[0].trim();
        String expr = parts[1].trim();

        String label = context.declareVariable(name);
        exprParser.compile(expr);
        context.emitter.emit("LDI", "R1", label);
        context.emitter.emit("ST", "R1", "R0");
    }

    private void handleAssignment(String line) {
        String content = line.replace(";", "").trim();
        String[] parts = content.split("=", 2);
        String name = parts[0].trim();
        String expr = parts[1].trim();

        if (!context.hasVariable(name)) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }

        exprParser.compile(expr);
        String label = context.getVariableLabel(name);
        context.emitter.emit("LDI", "R1", label);
        context.emitter.emit("ST", "R1", "R0");
    }

    private int handleIf(List<String> lines, int startIdx) {
        String header = lines.get(startIdx);

        // Защита: проверка наличия скобок
        int openBrace = header.indexOf('(');
        int closeBrace = header.lastIndexOf(')');
        if (openBrace == -1 || closeBrace == -1 || openBrace > closeBrace) {
            throw new IllegalArgumentException("Ошибка синтаксиса в 'if': ожидались скобки (condition) в строке: " + header);
        }

        String cond = header.substring(openBrace + 1, closeBrace).trim();
        if (cond.isEmpty()) {
            throw new IllegalArgumentException("Ошибка: условие 'if' не может быть пустым");
        }

        String elseLabel = context.nextLabel("else");
        String endLabel = context.nextLabel("endif");

        // Генерируем условие: если ложно — прыгаем на else
        compileCondition(cond, elseLabel, true);

        // Извлекаем тело блока IF
        BlockResult ifResult = extractBlockLines(lines, startIdx);
        // КРИТИЧЕСКИЙ ФИКС: сохраняем глобальный индекс в локальную переменную СРАЗУ
        int ifBlockEndIdx = ifResult.endIdx;

        // Рекурсивно парсим тело
        parseBlock(ifResult.lines);

        // Прыгаем в конец, чтобы не исполнять else
        context.emitter.emitJump("JMP", endLabel);

        // Метка начала ELSE
        context.emitter.emitLabel(elseLabel);

        int finalIdx = ifBlockEndIdx;

        // Проверяем наличие блока ELSE
        if (ifBlockEndIdx + 1 < lines.size()) {
            String nextLine = lines.get(ifBlockEndIdx + 1).trim();

            if (nextLine.startsWith("else")) {
                // Если else на той же строке что-то содержит или это просто ключевое слово
                BlockResult elseResult = extractBlockLines(lines, ifBlockEndIdx + 1);
                finalIdx = elseResult.endIdx;
                parseBlock(elseResult.lines);
            }
        }

        context.emitter.emitLabel(endLabel);
        return finalIdx; // Возвращаем индекс последней обработанной строки
    }

    private int handleWhile(List<String> lines, int startIdx) {
        String header = lines.get(startIdx);

        // Защита: проверка наличия скобок
        int openBrace = header.indexOf('(');
        int closeBrace = header.lastIndexOf(')');
        if (openBrace == -1 || closeBrace == -1 || openBrace > closeBrace) {
            throw new IllegalArgumentException("Ошибка синтаксиса в 'while': ожидались скобки (condition) в строке: " + header);
        }

        String cond = header.substring(openBrace + 1, closeBrace).trim();
        if (cond.isEmpty()) {
            throw new IllegalArgumentException("Ошибка: условие 'while' не может быть пустым");
        }

        String startLabel = context.nextLabel("while");
        String endLabel = context.nextLabel("endwhile");

        // Для поддержки инструкции 'break;' сохраняем метку конца цикла в стек
        context.pushLoopEnd(endLabel);

        context.emitter.emitLabel(startLabel);

        // Если условие ложно — выходим из цикла
        compileCondition(cond, endLabel, true);

        // Извлекаем тело блока WHILE
        BlockResult result = extractBlockLines(lines, startIdx);
        // КРИТИЧЕСКИЙ ФИКС: сохраняем индекс конца блока в локальную переменную
        int loopBlockEndIdx = result.endIdx;

        // Рекурсивно парсим тело
        parseBlock(result.lines);

        // Прыгаем в начало на проверку условия
        context.emitter.emitJump("JMP", startLabel);

        // Метка выхода
        context.emitter.emitLabel(endLabel);
        context.popLoopEnd();

        return loopBlockEndIdx;
    }

    private void compileCondition(String cond, String jumpLabel, boolean jumpIfFalse) {
        if (cond == null || cond.trim().isEmpty()) {
            throw new IllegalArgumentException("Ошибка: условие не может быть пустым");
        }

        // Порядок важен: сначала проверяем двухсимвольные операторы, чтобы ">=" не распознался как ">"
        String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
        String op = null;
        int opIdx = -1;

        // Ищем оператор, игнорируя те, что находятся внутри скобок (защита от mem[a==b] == c)
        for (String o : ops) {
            opIdx = findTopLevelOperator(cond, o);
            if (opIdx != -1) {
                op = o;
                break;
            }
        }

        if (op == null) {
            // Если оператора нет, считаем это выражением: if (expr != 0)
            exprParser.compile(cond); // Результат в R0
            context.emitter.emit("CMPI", "R0", "0"); // Сравниваем с 0

            // Если jumpIfFalse (стандарт для if/while):
            // Прыгаем, если R0 == 0 (JZ)
            if (jumpIfFalse) {
                context.emitter.emitJump("JZ", jumpLabel);
            } else {
                context.emitter.emitJump("JNZ", jumpLabel);
            }
            return;
        }

        String left = cond.substring(0, opIdx).trim();
        String right = cond.substring(opIdx + op.length()).trim();

        if (left.isEmpty() || right.isEmpty()) {
            throw new IllegalArgumentException("Ошибка: пропущен операнд в условии: " + cond);
        }

        // 1. Вычисляем левую часть -> результат в R0
        exprParser.compile(left);
        context.emitter.emit("PUSH", "R0");

        // 2. Вычисляем правую часть -> результат в R0
        // Используем новый экземпляр парсера для изоляции, если выражения сложные
        new ExpressionParser(context).compile(right);

        // 3. Подготовка к сравнению
        context.emitter.emit("MOV", "R1", "R0"); // Правая часть в R1
        context.emitter.emit("POP", "R0");        // Левая часть обратно в R0

        // Сравниваем R0 (left) и R1 (right)
        context.emitter.emit("CMP", "R0", "R1");

        // 4. Генерация прыжков
        if (jumpIfFalse) {
            // Прыгаем, если условие НЕ выполняется
            switch (op) {
                case "==" -> context.emitter.emitJump("JNZ", jumpLabel); // Прыжок, если не равно
                case "!=" -> context.emitter.emitJump("JZ",  jumpLabel); // Прыжок, если равно
                case ">"  -> {
                    context.emitter.emitJump("JL", jumpLabel); // Прыжок, если меньше
                    context.emitter.emitJump("JZ", jumpLabel); // Прыжок, если равно
                }
                case "<"  -> {
                    context.emitter.emitJump("JG", jumpLabel); // Прыжок, если больше
                    context.emitter.emitJump("JZ", jumpLabel); // Прыжок, если равно
                }
                case ">=" -> context.emitter.emitJump("JL", jumpLabel); // Прыжок, если меньше
                case "<=" -> context.emitter.emitJump("JG", jumpLabel); // Прыжок, если больше
            }
        } else {
            // Прыгаем, если условие ВЫПОЛНЯЕТСЯ (jumpIfTrue)
            switch (op) {
                case "==" -> context.emitter.emitJump("JZ",  jumpLabel);
                case "!=" -> context.emitter.emitJump("JNZ", jumpLabel);
                case ">"  -> context.emitter.emitJump("JG",  jumpLabel);
                case "<"  -> context.emitter.emitJump("JL",  jumpLabel);
                case ">=" -> {
                    context.emitter.emitJump("JG", jumpLabel);
                    context.emitter.emitJump("JZ", jumpLabel);
                }
                case "<=" -> {
                    context.emitter.emitJump("JL", jumpLabel);
                    context.emitter.emitJump("JZ", jumpLabel);
                }
            }
        }
    }

    /**
     * Вспомогательный метод для поиска оператора только на верхнем уровне вложенности скобок.
     * Защищает от ложного срабатывания на: if (mem[a == 1] == 2)
     */
    private int findTopLevelOperator(String cond, String op) {
        int balance = 0;
        for (int i = 0; i < cond.length(); i++) {
            char c = cond.charAt(i);
            if (c == '(' || c == '[') balance++;
            else if (c == ')' || c == ']') balance--;

            if (balance == 0) {
                if (cond.startsWith(op, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void handleCall(String line) {
        String content = line.replace(";", "").trim();
        int openParen = content.indexOf('(');
        String name = content.substring(0, openParen).trim();
        String argsRaw = content.substring(openParen + 1, content.lastIndexOf(')')).trim();

        // --- ИСПРАВЛЕНИЕ: Специальная обработка для call(address) ---
        if (name.equals("call")) {
            // Это низкоуровневый вызов по адресу: call(256);
            // Мы передаем аргумент (адрес) напрямую в ассемблерную инструкцию CALL
            if (argsRaw.isEmpty()) {
                throw new IllegalArgumentException("call() requires an address argument");
            }
            String target = argsRaw;
            if (context.hasConstant(target)) {
                target = context.getConstant(target);
            }
            context.emitter.emit("CALL", argsRaw.trim());
            return;
        }
        // -----------------------------------------------------------

        // Стандартная обработка вызова функций (main, gpu_clear и т.д.)
        if (!argsRaw.isEmpty()) {
            String[] args = argsStrSplit(argsRaw); // Используй безопасный сплит
            for (String arg : args) {
                exprParser.compile(arg.trim());
                context.emitter.emit("PUSH", "R0");
            }
            for (int i = args.length - 1; i >= 0; i--) {
                context.emitter.emit("POP", "R" + i);
            }
        }
        context.emitter.emitJump("CALL", name);
    }

    private String[] argsStrSplit(String argsRaw) {
        if (argsRaw.isEmpty()) return new String[0];
        return argsRaw.split(",");
    }

    private void handleMemWrite(String line) {
        String content = line.replace(";", "").trim();
        int closeBracket = content.indexOf(']');
        String addrExpr = content.substring(4, closeBracket).trim();
        String valExpr = content.substring(closeBracket + 1).replace("=", "").trim();

        exprParser.compile(valExpr);
        context.emitter.emit("PUSH", "R0");

        exprParser.compile(addrExpr);
        context.emitter.emit("MOV", "R1", "R0");

        context.emitter.emit("POP", "R0");
        context.emitter.emit("MOV", "R2", "R0");

        context.emitter.emit("ST", "R1", "R2");
    }

    private BlockResult extractBlockLines(List<String> lines, int startIdx) {
        List<String> block = new ArrayList<>();
        int depth = 0;
        int i = startIdx;
        boolean foundFirstBrace = false;

        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();

            for (int charIdx = 0; charIdx < line.length(); charIdx++) {
                char c = line.charAt(charIdx);
                if (c == '{') {
                    if (!foundFirstBrace) foundFirstBrace = true;
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }

            // Если мы уже нашли первую скобку, начинаем собирать строки
            if (foundFirstBrace) {
                // Если на текущей строке глубина стала 0, значит здесь конец блока
                if (depth == 0) {
                    // Но на этой же строке перед '}' может быть полезный код!
                    // Пример: "  halt; }"
                    String lastLineContent = trimmed.replace("}", "").trim();
                    if (!lastLineContent.isEmpty() && !lastLineContent.equals("{")) {
                        block.add(lastLineContent);
                    }
                    return new BlockResult(block, i); // Возвращаем текущий индекс
                }

                // Добавляем строку в блок, если это не чистая открывающая скобка
                // Но мы убираем саму скобку '{' из первой строки, чтобы не путать рекурсивный парсер
                if (i == startIdx) {
                    String firstLineContent = trimmed.replace("{", "").trim();
                    if (!firstLineContent.isEmpty()) {
                        block.add(firstLineContent);
                    }
                } else {
                    block.add(line); // Сохраняем оригинальные отступы для вложенных блоков
                }
            }

            i++;
        }

        // Если дошли до сюда и depth != 0 — программист забыл скобку.
        throw new IllegalArgumentException("Syntax Error: Unclosed curly brace starting at line " + startIdx);
    }
    private static class BlockResult {
        List<String> lines;
        int endIdx;
        BlockResult(List<String> lines, int endIdx) {
            this.lines = lines;
            this.endIdx = endIdx;
        }
    }
}