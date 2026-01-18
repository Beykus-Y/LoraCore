package com.loracore;

import com.loracore.lang.Assembler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BootSectorGenerator {

    public static void main(String[] args) {
        generate();
    }

    public static void generate() {
        Assembler asm = new Assembler();

        // =============================================================
        // LoraCore Boot Sector (Hello World)
        // Загружается BIOS'ом по адресу 0x1000 (4096)
        // ВАЖНО: Все метки переходов должны быть смещены на BASE_ADDR!
        // =============================================================
        final int BASE_ADDR = 4096;

        // --- 1. Повторный поиск GPU через PnP ---
        // Мы не полагаемся на то, что BIOS оставил нам регистры чистыми.
        // Ищем GPU сами. Это доказывает, что код реально работает.

        // R1 = PnP Pointer (0xFFF000)
        asm.lui(1, 0x00FF);
        asm.ori(1, 0xF000);

        // Пропускаем Magic (4 байта) и Count (4 байта) -> Идем к первому устройству
        asm.addi(1, 8);

        // R4 = Тип GPU (2)
        asm.ldi(4, 2);

        // --- Цикл поиска ---
        // ! FIX: Добавляем BASE_ADDR к текущему смещению
        int scanLoop = asm.getCurrentOffset() + BASE_ADDR;

        // Читаем тип устройства
        asm.ld(3, 1);

        // Сравниваем с GPU (2)
        asm.cmp(3, 4);
        asm.jz(0xFFFF); // Placeholder: Found
        int jumpToFound = asm.toIntArray().length - 1;

        // Если не GPU, идем к следующему (+16 байт)
        asm.addi(1, 16);
        asm.jmp(scanLoop); // Прыгаем на 4096 + смещение

        // --- GPU Найдена ---
        // ! FIX: Добавляем BASE_ADDR
        int foundLabel = asm.getCurrentOffset() + BASE_ADDR;
        asm.patchJump(jumpToFound, foundLabel);

        // Читаем VRAM Base Address из PnP (смещение +4 от Type)
        asm.addi(1, 4);
        asm.ld(5, 1); // R5 = VRAM Base Address

        // --- 2. Рисуем ЗЕЛЕНЫЙ КВАДРАТ ---
        // R7 = Зеленый цвет (Alpha=255, R=0, G=255, B=0 -> 0xFF00FF00)
        asm.lui(7, 0xFF00);
        asm.ori(7, 0xFF00);

        // Нарисуем линию или квадрат
        // R6 = Счетчик
        asm.ldi(6, 0);
        // Лимит (например, 10000 пикселей)
        asm.ldi(8, 10000);

        // ! FIX: Добавляем BASE_ADDR
        int drawLoop = asm.getCurrentOffset() + BASE_ADDR;

        asm.cmp(6, 8);
        asm.jz(0xFFFF); // Placeholder: End
        int jumpToEnd = asm.toIntArray().length - 1;

        // Рисуем пиксель: [Base + Offset] = Green
        // Вычисляем адрес: R9 = R5 + R6
        asm.mov(9, 5);
        asm.add(9, 6);

        asm.st(9, 7); // Запись в видеопамять

        asm.addi(6, 4); // Следующий пиксель (+4 байта)
        asm.jmp(drawLoop);

        // --- 3. Конец (Halt) ---
        // ! FIX: Добавляем BASE_ADDR
        int endLabel = asm.getCurrentOffset() + BASE_ADDR;
        asm.patchJump(jumpToEnd, endLabel);

        asm.hlt(); // Останавливаем процессор
        asm.jmp(endLabel); // На всякий случай

        // --- Сохранение ---
        // Сохраняем как disk.bin
        byte[] code = asm.toByteArray();

        // Нам нужно, чтобы файл был размером хотя бы 512 байт (1 сектор),
        // иначе контроллер может ругаться на выход за границы при чтении.
        byte[] diskImage = new byte[1024 * 1024]; // 1 MB диск
        System.arraycopy(code, 0, diskImage, 0, code.length);

        File file = new File("src/main/resources/assets/loracore/os/disk.bin");
        file.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(diskImage);
            System.out.println("Boot Disk generated: " + file.getAbsolutePath());
            System.out.println("Code size: " + code.length + " bytes");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}