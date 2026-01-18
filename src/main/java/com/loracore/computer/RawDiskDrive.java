package com.loracore.computer;

import com.loracore.LoraCoreMod;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Класс для низкоуровневой работы с файлом жесткого диска.
 * Использует RandomAccessFile для чтения/записи конкретных секторов
 * без загрузки всего файла в оперативную память.
 */
public class RawDiskDrive {
    private final Path diskPath;
    private final int sizeBytes;

    public RawDiskDrive(Path folder, String filename, int sizeBytes) {
        this.diskPath = folder.resolve(filename);
        this.sizeBytes = sizeBytes;
    }

    /**
     * Читает 512 байт (1 сектор) из файла.
     */
    public synchronized void readSector(int lba, byte[] buffer, int offset) throws IOException {
        long fileOffset = (long) lba * 512;
        if (fileOffset >= sizeBytes) {
            // Чтение за пределами диска возвращает нули
            Arrays.fill(buffer, offset, offset + 512, (byte) 0);
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(diskPath.toFile(), "r")) {
            raf.seek(fileOffset);
            int read = raf.read(buffer, offset, 512);
            // Если прочитали меньше 512 байт (конец файла), остаток заполняем нулями
            if (read < 512) {
                int startFill = (read == -1) ? 0 : read;
                Arrays.fill(buffer, offset + startFill, offset + 512, (byte) 0);
            }
        }
    }

    /**
     * Записывает 512 байт (1 сектор) в файл.
     */
    public synchronized void writeSector(int lba, byte[] data, int offset) throws IOException {
        long fileOffset = (long) lba * 512;
        if (fileOffset >= sizeBytes) {
            return; // Игнорируем запись за пределы
        }

        try (RandomAccessFile raf = new RandomAccessFile(diskPath.toFile(), "rw")) {
            raf.seek(fileOffset);
            raf.write(data, offset, 512);
        }
    }

    /**
     * Гарантирует существование файла диска.
     * Если файла нет, создает его и заполняет нулями до нужного размера.
     */
    public void ensureExists() throws IOException {
        if (!Files.exists(diskPath)) {
            Files.createDirectories(diskPath.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(diskPath.toFile(), "rw")) {
                raf.setLength(sizeBytes);
            }
            LoraCoreMod.LOGGER.info("Created new raw disk image at: {}", diskPath);
        }
    }
}