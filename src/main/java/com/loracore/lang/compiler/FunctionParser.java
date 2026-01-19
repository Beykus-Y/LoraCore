package com.loracore.lang.compiler;

import java.util.ArrayList;
import java.util.List;

public class FunctionParser {
    private final CompilerContext context;
    private final StatementParser statementParser;

    public FunctionParser(CompilerContext context) {
        this.context = context;
        this.statementParser = new StatementParser(context);
    }

    public void parse(List<String> lines) {
        int i = 0;
        while (i < lines.size()) {
            String originalLine = lines.get(i);
            String line = originalLine.trim();

            // Пропускаем пустые строки и комментарии
            if (line.isEmpty() || line.startsWith("//")) {
                i++;
                continue;
            }

            // 1. Глобальные переменные (let)
            // Мы проверяем "let ", чтобы не поймать переменную "letter"
            if (line.startsWith("let ")) {
                // Передаем именно trimmed строку, чтобы handleGlobalLet
                // гарантированно нашел "let " по индексу 0
                handleGlobalLet(line);
                i++;
                continue;
            }

            // 2. Функции (fn)
            if (line.startsWith("fn ")) {
                // handleFunction вернет индекс строки с закрывающей скобкой '}'
                int lastFuncLine = handleFunction(lines, i);

                // КРИТИЧЕСКИЙ МОМЕНТ:
                // Переходим сразу за пределы функции.
                // Если handleFunction вернул 10, следующая строка — 11.
                i = lastFuncLine + 1;
                continue;
            }

            // 3. Константы (const)
            // Даже если их обработал препроцессор, парсер должен знать, что это не мусор
            if (line.startsWith("const ")) {
                i++;
                continue;
            }

            // Если мы здесь, значит нашли код, который не влез в 'let', 'fn' или 'const'
            // В глобальной области Си-подобного языка такому не место.
            // Исключение — одинокие фигурные скобки, которые могли остаться после препроцессора
            if (!line.equals("}") && !line.equals("{")) {
                throw new IllegalArgumentException(
                        "Syntax Error: Code found outside of any function at line " + (i + 1) + ": [" + line + "]"
                );
            }

            i++;
        }
    }

    private void handleGlobalLet(String line) {
        String content = line.substring(4).replace(";", "").trim();
        String[] parts = content.split("=", 2);

        if (parts.length < 1) {
            throw new IllegalArgumentException("Ошибка: неверный формат 'let' в глобальной области: " + line);
        }

        String name = parts[0].trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Ошибка: пропущено имя переменной в: " + line);
        }

        int val = 0;
        if (parts.length > 1) {
            try {
                String valStr = parts[1].trim();
                if (valStr.startsWith("0x") || valStr.startsWith("0X")) {
                    val = (int) Long.parseUnsignedLong(valStr.substring(2), 16);
                } else {
                    val = Integer.parseInt(valStr);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Ошибка: глобальная переменная '" + name + "' должна быть константой. Получено: " + parts[1]);
            }
        }
        context.declareGlobal(name, val);
    }

    private int handleFunction(List<String> lines, int startIdx) {
        String line = lines.get(startIdx).trim();

        // Извлекаем заголовок (имя и аргументы)
        String header = line.substring(3).trim();
        if (header.contains("{")) {
            header = header.substring(0, header.indexOf('{')).trim();
        }

        int openParen = header.indexOf('(');
        int closeParen = header.lastIndexOf(')');

        if (openParen == -1 || closeParen == -1) {
            throw new IllegalArgumentException("Ошибка синтаксиса функции: пропущены скобки () в " + line);
        }

        String funcName = header.substring(0, openParen).trim();
        String argsStr = header.substring(openParen + 1, closeParen).trim();

        // Инициализация контекста функции
        context.clearVariables();
        context.emitter.emitComment("--- Function: " + funcName + " ---");
        context.emitter.emitLabel(funcName);

        String exitLabel = context.nextLabel("exit_" + funcName);
        context.setCurrentFunctionExit(exitLabel);

        // Регистрация аргументов (R0-R7)
        if (!argsStr.isEmpty()) {
            String[] args = argsStr.split(",");
            for (int j = 0; j < args.length; j++) {
                String argName = args[j].trim();
                if (argName.isEmpty()) continue;

                if (j >= 8) throw new IllegalArgumentException("Ошибка: слишком много аргументов в " + funcName);

                String label = "arg_" + funcName + "_" + argName + "_" + context.currentLabelIndex();
                context.emitter.emitData(label, 0);

                // Сохраняем входящие регистры в статические переменные
                context.emitter.emit("LDI", "R12", label);
                context.emitter.emit("ST", "R12", "R" + j);
                context.registerVariable(argName, label);
            }
        }

        // --- ИЗВЛЕЧЕНИЕ ТЕЛА ---
        BlockResult result = extractBlockLines(lines, startIdx);

        // Передаем список строк тела функции в StatementParser
        statementParser.parseBlock(result.lines);

        // Завершение
        context.emitter.emitLabel(exitLabel);
        context.emitter.emit("RET");
        context.setCurrentFunctionExit(null);

        return result.endIdx; // Возвращаем индекс строки с '}'
    }

    /**
     * extractBlockLines - Честный поиск границ блока { ... }.
     * Не ломается, если код начинается сразу за скобкой.
     */
    private BlockResult extractBlockLines(List<String> lines, int startIdx) {
        List<String> block = new ArrayList<>();
        int depth = 0;
        int i = startIdx;
        boolean foundStart = false;

        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();

            for (char c : line.toCharArray()) {
                if (c == '{') {
                    depth++;
                    foundStart = true;
                } else if (c == '}') {
                    depth--;
                }
            }

            if (foundStart) {
                if (depth == 0) {
                    // Конец блока. Если на этой строке есть код ДО скобки, забираем его.
                    String codeBeforeBrace = line.substring(0, line.lastIndexOf('}')).trim();
                    if (!codeBeforeBrace.isEmpty() && !codeBeforeBrace.equals("{")) {
                        block.add(codeBeforeBrace);
                    }
                    return new BlockResult(block, i);
                }

                // Если это первая строка (с 'fn' или 'if'), берем только то, что ПОСЛЕ '{'
                if (i == startIdx) {
                    if (line.contains("{")) {
                        String after = line.substring(line.indexOf('{') + 1).trim();
                        if (!after.isEmpty()) block.add(after);
                    }
                } else {
                    block.add(line);
                }
            }
            i++;
        }
        throw new IllegalArgumentException("Syntax Error: Не найдена закрывающая скобка '}' для блока, начатого на строке " + (startIdx + 1));
    }

    /**
     * Вспомогательный класс, чтобы Java не ругалась на отсутствие символов.
     */
    private static class BlockResult {
        List<String> lines;
        int endIdx;
        BlockResult(List<String> lines, int endIdx) {
            this.lines = lines;
            this.endIdx = endIdx;
        }
    }
}