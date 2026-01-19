package com.loracore.lang.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Preprocessor {
    // Храним список уже импортированных файлов, чтобы избежать зацикливания (A -> B -> A)
    private static final Set<String> importedFiles = new HashSet<>();
    private static Path rootDirectory; // Корневая папка исходников

    public static List<String> process(String rootPathStr, String mainFileName, CompilerContext context) throws IOException {
        importedFiles.clear();
        rootDirectory = Paths.get(rootPathStr);

        return processFile(mainFileName, context);
    }

    // Временная совместимость для старого вызова
    public static List<String> process(String source, CompilerContext context) {
        // Если вызвали этот метод, считаем, что импортов нет или они не работают
        return standardizeSource(source, context);
    }

    private static List<String> processFile(String fileName, CompilerContext context) throws IOException {
        Path filePath = rootDirectory.resolve(fileName);
        // Приводим к абсолютному пути для проверки уникальности
        String absPath = filePath.toAbsolutePath().toString();

        if (importedFiles.contains(absPath)) {
            return new ArrayList<>(); // Файл уже был включен, пропускаем
        }
        importedFiles.add(absPath);

        System.out.println("Importing: " + fileName);
        String source = Files.readString(filePath);

        // Стандартизируем (расставляем скобки и точки с запятой)
        // Но теперь нам нужно делать это аккуратно, чтобы не сломать строки импортов до их обработки
        // Поэтому сначала разбиваем на строки, ищем импорты, а потом стандартизируем остаток.

        // Упрощенный подход: сначала стандартизируем весь текст, потом ищем import
        List<String> rawLines = standardizeSource(source, context);
        List<String> finalLines = new ArrayList<>();

        for (String line : rawLines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("import ")) {
                // Формат: import "drivers/gpu.lc";
                int startQ = line.indexOf('"');
                int endQ = line.lastIndexOf('"');

                if (startQ != -1 && endQ != -1 && endQ > startQ) {
                    String importPath = line.substring(startQ + 1, endQ);
                    // Рекурсивный вызов
                    List<String> importedLines = processFile(importPath, context);
                    finalLines.addAll(importedLines);
                } else {
                    throw new IllegalArgumentException("Invalid import syntax: " + line);
                }
            } else {
                finalLines.add(line);
            }
        }
        return finalLines;
    }

    private static List<String> standardizeSource(String source, CompilerContext context) {
        List<String> cleanLines = new ArrayList<>();
        // Та же магия замены, что и была
        String standardized = source
                .replace("{", "\n{\n")
                .replace("}", "\n}\n")
                .replace(";", ";\n");

        String[] rawLines = standardized.split("\n");

        for (String line : rawLines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            if (line.startsWith("const ")) {
                parseConstant(line, context);
            } else {
                cleanLines.add(line);
            }
        }
        return cleanLines;
    }

    private static void parseConstant(String line, CompilerContext context) {
        String content = line.substring(6).replace(";", "").trim();
        String[] parts = content.split("=");
        if (parts.length == 2) {
            context.addConstant(parts[0].trim(), parts[1].trim());
        }
    }
}