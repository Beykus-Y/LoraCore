package com.loracore;

import com.loracore.lang.TextAssembler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Расширенный генератор BIOS для LoraCore.
 * Компилирует bios.asm в бинарный образ и выводит отладочную информацию.
 */
public class BiosGenerator {

    private static final String SRC_PATH = "src/main/resources/assets/loracore/os/src/bios.asm";
    private static final String OUT_PATH = "src/main/resources/assets/loracore/os/bios.bin";
    private static final int MAX_BIOS_SIZE = 4096; // Лимит сегмента 4КБ

    public static void main(String[] args) {
        generate();
    }

    public static void generate() {
        System.out.println("========================================");
        System.out.println("   LoraCore BIOS Compilation Tool v2.0  ");
        System.out.println("========================================");

        try {
            // 1. Загрузка исходного кода
            Path sourcePath = Path.of(SRC_PATH);
            if (!Files.exists(sourcePath)) {
                System.err.println("[ERROR] Source file not found: " + sourcePath.toAbsolutePath());
                return;
            }

            System.out.println("[1/4] Reading source: " + sourcePath.getFileName());
            String source = Files.readString(sourcePath);
            System.out.println("      Source size: " + source.length() + " chars");

            // 2. Компиляция
            System.out.println("[2/4] Compiling ASM to Machine Code...");
            TextAssembler compiler = new TextAssembler();
            byte[] binary = compiler.compile(source);

            if (binary == null || binary.length == 0) {
                System.err.println("[ERROR] Compilation failed: output is empty!");
                return;
            }

            // 3. Аналитика
            int instrCount = binary.length / 4;
            System.out.println("[3/4] Analyzing binary...");
            System.out.println("      Instruction count: " + instrCount);
            System.out.println("      Final size: " + binary.length + " bytes");

            if (binary.length > MAX_BIOS_SIZE) {
                System.err.println("[WARN] BIOS size exceeds standard 4KB limit!");
            }

            // Вывод Hex-дампа первых строк для проверки
            printDebugHex(binary);

            // 4. Сохранение
            File outFile = new File(OUT_PATH);
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(binary);
                System.out.println("[4/4] BIOS saved successfully!");
                System.out.println("      Path: " + outFile.getAbsolutePath());
            }

            System.out.println("========================================");
            System.out.println("   BUILD SUCCESSFUL");
            System.out.println("========================================");

        } catch (IOException e) {
            System.err.println("[FATAL ERROR] IO Exception during generation:");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[FATAL ERROR] Unexpected compiler crash:");
            e.printStackTrace();
        }
    }

    /**
     * Выводит красивый Hex-дамп первых байтов прошивки.
     */
    private static void printDebugHex(byte[] data) {
        System.out.println("\n--- Binary Preview (First 64 bytes) ---");
        int len = Math.min(data.length, 64);
        for (int i = 0; i < len; i++) {
            System.out.printf("%02X ", data[i]);
            if ((i + 1) % 4 == 0) System.out.print(" "); // Группировка по инструкциям (32-bit)
            if ((i + 1) % 16 == 0) System.out.println();
        }
        if (data.length > 64) System.out.println("... (truncated)");
        System.out.println("---------------------------------------\n");
    }
}