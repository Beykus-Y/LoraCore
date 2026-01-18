package com.loracore;

import com.loracore.lang.LoraCompiler;
import com.loracore.lang.TextAssembler;
import java.nio.file.*;

public class LoraCGenerator {
    public static void main(String[] args) throws Exception {
        String path = "src/main/resources/assets/loracore/os/src/";

        // 1. Читаем LoraC
        String loraCCode = Files.readString(Paths.get(path + "bios.lc"));

        // 2. Компилируем в ASM
        LoraCompiler compiler = new LoraCompiler();
        String asmCode = compiler.compile(loraCCode);
        Files.writeString(Paths.get(path + "bios_gen.asm"), asmCode);

        // 3. Собираем в BIN
        TextAssembler assembler = new TextAssembler();
        byte[] binary = assembler.compile(asmCode);

        // 4. Сохраняем как BiosN.bin
        Files.write(Paths.get("src/main/resources/assets/loracore/os/BiosN.bin"), binary);

        System.out.println("BiosN.bin успешно создан из bios.lc!");
    }
}