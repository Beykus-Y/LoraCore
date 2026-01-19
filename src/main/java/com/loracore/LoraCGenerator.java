package com.loracore;

import com.loracore.lang.LoraCompiler;
import com.loracore.lang.TextAssembler;
import java.nio.file.*;

public class LoraCGenerator {
    public static void main(String[] args) throws Exception {
        // Путь к папке с исходниками
        String srcDir = "src/main/resources/assets/loracore/os/src/kernel";

        System.out.println("Compiling Kernel from: " + srcDir);

        // 1. Компилируем (используем новую перегрузку с поддержкой импортов)
        LoraCompiler compiler = new LoraCompiler();
        // Передаем папку и имя входного файла
        String asmCode = compiler.compile(srcDir, "kernel.lc");

        // Сохраняем ASM для отладки
        Files.writeString(Paths.get(srcDir + "/kernel.asm"), asmCode);

        // 2. Ассемблируем в BIN
        TextAssembler assembler = new TextAssembler();
        // Base Address = 0x2000 (куда Bootloader грузит ядро)
        byte[] binary = assembler.compile(asmCode, 0x2000);

        // 3. Сохраняем map-файл
        StringBuilder mapFile = new StringBuilder();
        for (String entry : assembler.getDebugMap()) {
            mapFile.append(entry).append("\n");
        }
        Files.writeString(Paths.get(srcDir + "/kernel.map"), mapFile.toString());

        // 4. Сохраняем готовый бинарник
        Files.write(Paths.get("src/main/resources/assets/loracore/os/kernel.bin"), binary);

        System.out.println("SUCCESS! kernel.bin generated (" + binary.length + " bytes)");
    }
}