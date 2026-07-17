package com.loracore.computer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Installs and safely migrates the factory LoraCore 2.x disk layout. */
final class FactoryDiskInstaller {
    static final int CONFIG_MAGIC_OFFSET = 5 * 512;
    static final int CONFIG_MAGIC = 0x3242444C;

    enum Result { CREATED, CURRENT, MIGRATED }

    private FactoryDiskInstaller() {}

    static Result ensureInstalled(RawDiskDrive drive, Path diskPath, int capacityBytes,
                                  byte[] factoryImage) throws IOException {
        if (factoryImage.length > capacityBytes) {
            throw new IOException("Factory image requires " + factoryImage.length
                    + " bytes, disk capacity is " + capacityBytes);
        }

        boolean existed = Files.exists(diskPath);
        if (!existed) {
            drive.ensureExists();
            writeFreshImage(diskPath, capacityBytes, factoryImage);
            return Result.CREATED;
        }
        if (hasCurrentLayout(diskPath)) {
            return Result.CURRENT;
        }

        Path backup = diskPath.resolveSibling(diskPath.getFileName() + ".pre-2.0.bak");
        if (!Files.exists(backup)) {
            Files.copy(diskPath, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        writeFreshImage(diskPath, capacityBytes, factoryImage);
        return Result.MIGRATED;
    }

    private static boolean hasCurrentLayout(Path diskPath) throws IOException {
        if (Files.size(diskPath) < CONFIG_MAGIC_OFFSET + 4L) {
            return false;
        }
        byte[] magic = new byte[4];
        try (RandomAccessFile disk = new RandomAccessFile(diskPath.toFile(), "r")) {
            disk.seek(CONFIG_MAGIC_OFFSET);
            disk.readFully(magic);
        }
        return ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN).getInt() == CONFIG_MAGIC;
    }

    private static void writeFreshImage(Path diskPath, int capacityBytes,
                                        byte[] factoryImage) throws IOException {
        Files.createDirectories(diskPath.getParent());
        try (RandomAccessFile disk = new RandomAccessFile(diskPath.toFile(), "rw")) {
            disk.setLength(0);
            disk.setLength(capacityBytes);
            disk.seek(0);
            disk.write(factoryImage);
        }
    }
}
