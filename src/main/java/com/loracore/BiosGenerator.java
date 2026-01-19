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

    public static void main(String[] args) {
        // 1. Компилируем BIOS (адрес 0x0000)
        compile("BIOS", BIOS_SRC, BIOS_OUT, 0x0000);

        // 2. Компилируем Bootloader (адрес 0x1000)
        // Он будет загружен в память по адресу 4096 загрузчиком BIOS'а
        compile("Bootloader", BOOT_SRC, BOOT_OUT, 0x1000);
    }

    private static void compile(String name, String src, String out, int baseAddr) {
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

            // Запись чистого бинарника
            File outFile = new File(out);
            if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(binary);
            }

            System.out.println("OK: " + out + " (" + binary.length + " bytes)");

        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}