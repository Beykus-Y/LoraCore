package lora.emulator;

import lora.emulator.bus.*;
import lora.emulator.memory.RamStick;
import lora.emulator.util.AsmLoader;
import lora.emulator.util.LoraCompiler;
import lora.emulator.gui.DebugWindow;
import lora.emulator.gui.LoraKeyHandler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class LoraEmulatorMain {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Lora Emulator System Init ===");

        // 1. Читаем и компилируем ядро
        // Линус: В идеале ядро должно лежать на диске, но для тестов сойдет и так.
        String sourcePath = "src/main/resources/kernel.lc";

        // Проверка на дурака
        if (!Files.exists(Path.of(sourcePath))) {
            System.err.println("[Fatal] Kernel source not found: " + sourcePath);
            return;
        }

        String kernelSource = Files.readString(Path.of(sourcePath));

        System.out.println("[Compiler] Compiling " + sourcePath + "...");
        LoraCompiler compiler = new LoraCompiler();
        String generatedAsm = compiler.compile(kernelSource);

        // Превращаем сгенерированный ASM в байт-код
        List<String> asmLines = Arrays.asList(generatedAsm.split("\n"));
        byte[] fullKernelBin = AsmLoader.assemble(asmLines);

        // Линковка: отрезаем паддинг до точки входа ядра
        int startOffset = 0x500;
        byte[] kernelBin;
        if (fullKernelBin.length > startOffset) {
            kernelBin = Arrays.copyOfRange(fullKernelBin, startOffset, fullKernelBin.length);
            System.out.printf("[Linker] Stripped %d bytes of padding. Real size: %d bytes\n", startOffset, kernelBin.length);
        } else {
            kernelBin = fullKernelBin;
        }

        // 2. Готовим диск
        File hddImage = new File("disk.img");
        prepareDisk(kernelBin, hddImage);

        // 3. Собираем железо
        // Используем константы из LoraComputer, чтобы не прострелить себе ногу с адресами
        LoraComputer pc = new LoraComputer(CpuTiers.TIER_2_MID_RANGE, 12345L);

        // Основная RAM: 0x0000. 8KB маловато для серьезных дел, но для Hello World хватит.
        pc.mapDevice(LoraComputer.ADDR_RAM_MAIN, new RamStick(8192, "MainRAM"));

        // Шрифт ROM: 0x2000
        pc.mapDevice(LoraComputer.ADDR_ROM_FONT, new FontRom());

        // Видеокарта: 0x4000
        GpuDevice gpu = new GpuDevice(GpuSpecs.TIER_2_ACCEL);
        pc.mapDevice(LoraComputer.ADDR_GPU_VRAM, gpu);

        // --- MMIO Devices (High Memory) ---
        // Теперь они живут далеко от видеопамяти.

        KeyboardDevice kb = new KeyboardDevice();
        pc.mapDevice(LoraComputer.ADDR_KEYBOARD, kb); // 0xF00000

        pc.mapDevice(LoraComputer.ADDR_DISK_CTRL, new DiskDevice(pc.getBus(), hddImage)); // 0xF01000

        pc.mapDevice(LoraComputer.ADDR_NIC, new LoraNic()); // 0xF02000

        pc.mapDevice(LoraComputer.ADDR_CMOS, new CmosDevice(new File("cmos.bin"))); // 0xF03000

        // 4. GUI
        DebugWindow window = new DebugWindow(gpu, 4);
        window.addKeyListener(new LoraKeyHandler(kb));

        // 5. Прошивка BIOS и запуск
        try {
            byte[] biosBin = AsmLoader.loadFromResources("bios.asm");
            pc.flashMemory(0, biosBin);
        } catch (Exception e) {
            System.err.println("[Boot] Failed to load BIOS: " + e.getMessage());
            // Продолжаем без BIOS? Нет, это смерть.
            System.exit(1);
        }

        pc.start();

        System.out.println("[System] Running. Devices mapped safely.");
        System.out.println("         RAM:  0x" + Integer.toHexString(LoraComputer.ADDR_RAM_MAIN));
        System.out.println("         GPU:  0x" + Integer.toHexString(LoraComputer.ADDR_GPU_VRAM));
        System.out.println("         MMIO: 0x" + Integer.toHexString(LoraComputer.MMIO_BASE));

        // Мониторинг (в отдельном потоке или просто тут)
        while (true) {
            Thread.sleep(1000);
            System.out.printf("[System] %.1f°C | PC: 0x%04X | Errors: %d \r",
                    pc.getTemperature(), pc.getCpu().pc, pc.getErrorCount());
        }
    }

    private static void prepareDisk(byte[] code, File imgFile) throws IOException {
        if (imgFile.exists()) imgFile.delete();
        try (RandomAccessFile raf = new RandomAccessFile(imgFile, "rw")) {
            raf.write(code);
            // Если диск меньше мегабайта, система может не понять геометрию
            raf.setLength(1024 * 1024); // 1MB image
            System.out.println("[DiskPrep] Kernel flashed to sector 0.");
        }
    }
}