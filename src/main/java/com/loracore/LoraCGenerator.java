package com.loracore;

import com.loracore.lang.LoraCompiler;
import com.loracore.lang.TextAssembler;
import java.nio.file.*;

public class LoraCGenerator {
    private static final Path SOURCE_DIR = Paths.get(
            "src/main/resources/assets/loracore/os/src/kernel");
    private static final Path SYSTEM_ASSEMBLY = Paths.get(
            "src/main/resources/assets/loracore/os/src/kernel.asm");
    private static final Path RECOVERY_ASSEMBLY = Paths.get(
            "src/main/resources/assets/loracore/os/src/recovery.asm");
    private static final Path OS_DIR = Paths.get(
            "src/main/resources/assets/loracore/os");

    public static void main(String[] args) throws Exception {
        compile("LoraOS", "kernel.lc", SYSTEM_ASSEMBLY,
                OS_DIR.resolve("system.map"), OS_DIR.resolve("system.bin"));
        compile("Recovery", "recovery.lc", RECOVERY_ASSEMBLY,
                OS_DIR.resolve("recovery.map"), OS_DIR.resolve("recovery.bin"));
    }

    private static void compile(String name, String sourceName, Path assemblyPath,
                                Path mapPath, Path binaryPath) throws Exception {
        System.out.println("Compiling " + name + " from: " + SOURCE_DIR.resolve(sourceName));
        LoraCompiler compiler = new LoraCompiler();
        String asmCode = compiler.compile(SOURCE_DIR.toString(), sourceName);

        Files.writeString(assemblyPath, asmCode);

        TextAssembler assembler = new TextAssembler();
        byte[] binary = assembler.compile(asmCode, 0x2000);

        StringBuilder mapFile = new StringBuilder();
        for (String entry : assembler.getDebugMap()) {
            mapFile.append(entry).append("\n");
        }
        Files.writeString(mapPath, mapFile.toString());

        Files.write(binaryPath, binary);

        System.out.println("SUCCESS! " + binaryPath.getFileName()
                + " generated (" + binary.length + " bytes)");
    }
}
