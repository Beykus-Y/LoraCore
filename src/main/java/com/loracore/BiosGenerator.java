package com.loracore;

import com.loracore.lang.TextAssembler;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Утилита для компиляции системных файлов (BIOS, Bootloader).
 */
public class BiosGenerator {

    // Пути к исходникам и бинарникам
    private static final String BASE_RES_PATH = "src/main/resources/assets/loracore/os/";
    private static final String BIOS_SRC = BASE_RES_PATH + "src/bios.asm";
    private static final String BIOS_OUT = BASE_RES_PATH + "bios.bin";
    private static final String BOOT_SRC = BASE_RES_PATH + "src/boot.asm";
    private static final String BOOT_OUT = BASE_RES_PATH + "boot.bin";
    private static final String LOADER_SRC = BASE_RES_PATH + "src/loader.asm";
    private static final String LOADER_OUT = BASE_RES_PATH + "loader.bin";

    public static void main(String[] args) {
        // 1. Компилируем BIOS (адрес 0x0000)
        compile("BIOS", BIOS_SRC, BIOS_OUT, 0x0000, Integer.MAX_VALUE);

        // 2. Компилируем Bootloader (адрес 0x1000)
        // Он будет загружен в память по адресу 4096 загрузчиком BIOS'а
        compile("Stage-1 boot sector", BOOT_SRC, BOOT_OUT, 0x1000, 512);

        // Stage 1 loads the extensible dualboot loader into the next four sectors.
        compile("Stage-2 dualboot loader", LOADER_SRC, LOADER_OUT, 0x1200, 4 * 512);
    }

    private static void compile(String name, String src, String out, int baseAddr, int maxSize) {
        System.out.println("--- Compiling " + name + " ---");
        try {
            Path srcPath = Path.of(src);
            if (!Files.exists(srcPath)) {
                System.err.println("File not found: " + src);
                return;
            }

            String sourceCode = Files.readString(srcPath);
            TextAssembler assembler = new TextAssembler();

            // Компиляция
            byte[] binary = assembler.compile(sourceCode, baseAddr);
            if (binary.length > maxSize) {
                throw new IllegalStateException(name + " is " + binary.length
                        + " bytes; maximum is " + maxSize);
            }

            // Запись чистого бинарника
            File outFile = new File(out);
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(binary);
            }

            System.out.println("OK: " + out + " (" + binary.length + " bytes)");

        } catch (Exception e) {
            throw new IllegalStateException("Failed to compile " + name, e);
        }
    }
}
