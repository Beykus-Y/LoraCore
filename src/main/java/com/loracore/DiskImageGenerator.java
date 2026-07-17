package com.loracore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/** Creates the complete factory disk shipped inside the mod JAR. */
public final class DiskImageGenerator {
    static final int SECTOR_SIZE = 512;
    static final int DISK_SIZE = 1024 * 1024;
    static final int STAGE2_LBA = 1;
    static final int STAGE2_SECTORS = 4;
    static final int CONFIG_LBA = 5;
    static final int TABLE_LBA = 6;
    static final int TABLE_SECTORS = 2;
    static final int ENTRY_SIZE = 64;
    static final int ENTRY_COUNT = 16;
    static final int FIRST_DATA_LBA = 16;
    static final int CONFIG_MAGIC = 0x3242444C; // "LDB2" in little endian
    private static final Path OS_DIR = Path.of("src/main/resources/assets/loracore/os");

    private record SystemImage(String name, byte[] content) {}

    private DiskImageGenerator() {}

    public static void main(String[] args) throws IOException {
        byte[] stage1 = readChecked("boot.bin", SECTOR_SIZE);
        byte[] stage2 = readChecked("loader.bin", STAGE2_SECTORS * SECTOR_SIZE);
        SystemImage[] systems = {
                new SystemImage("system.bin", Files.readAllBytes(OS_DIR.resolve("system.bin"))),
                new SystemImage("recovery.bin", Files.readAllBytes(OS_DIR.resolve("recovery.bin")))
        };

        byte[] disk = new byte[DISK_SIZE];
        System.arraycopy(stage1, 0, disk, 0, stage1.length);
        System.arraycopy(stage2, 0, disk, STAGE2_LBA * SECTOR_SIZE, stage2.length);
        writeConfig(disk);

        int nextLba = FIRST_DATA_LBA;
        for (int index = 0; index < systems.length; index++) {
            SystemImage system = systems[index];
            int dataOffset = Math.multiplyExact(nextLba, SECTOR_SIZE);
            if (dataOffset + system.content.length > disk.length) {
                throw new IOException("Factory disk is too small for " + system.name);
            }
            System.arraycopy(system.content, 0, disk, dataOffset, system.content.length);
            writeEntry(disk, index, system, nextLba);
            nextLba += sectorsFor(system.content.length);
        }

        Path diskPath = OS_DIR.resolve("disk.bin");
        Files.write(diskPath, disk);
        System.out.printf("Factory disk generated: %s (LoraOS=%d, Recovery=%d bytes)%n",
                diskPath, systems[0].content.length, systems[1].content.length);
    }

    private static byte[] readChecked(String name, int maxSize) throws IOException {
        byte[] content = Files.readAllBytes(OS_DIR.resolve(name));
        if (content.length > maxSize) {
            throw new IOException(name + " is " + content.length + " bytes; maximum is " + maxSize);
        }
        return content;
    }

    private static void writeConfig(byte[] disk) {
        int offset = CONFIG_LBA * SECTOR_SIZE;
        ByteBuffer config = ByteBuffer.wrap(disk).order(ByteOrder.LITTLE_ENDIAN);
        config.putInt(offset, CONFIG_MAGIC);
        config.putInt(offset + 4, 1);  // format version
        config.putInt(offset + 8, 0);  // default boot slot: LoraOS
        config.putInt(offset + 12, 40); // menu timeout ticks
        config.putInt(offset + 16, 0); // slot 0 table entry
        config.putInt(offset + 20, 1); // slot 1 table entry
        config.putInt(offset + 24, 0); // flags, reserved
    }

    private static void writeEntry(byte[] disk, int index, SystemImage system, int lba) {
        if (index < 0 || index >= ENTRY_COUNT) {
            throw new IllegalArgumentException("Invalid table entry index: " + index);
        }
        int offset = TABLE_LBA * SECTOR_SIZE + index * ENTRY_SIZE;
        byte[] name = system.name.getBytes(StandardCharsets.US_ASCII);
        if (name.length > 39) {
            throw new IllegalArgumentException("System image name is too long: " + system.name);
        }
        System.arraycopy(name, 0, disk, offset, name.length);

        ByteBuffer table = ByteBuffer.wrap(disk).order(ByteOrder.LITTLE_ENDIAN);
        table.putInt(offset + 40, 1); // active
        table.putInt(offset + 44, lba);
        table.putInt(offset + 48, system.content.length);
        table.putInt(offset + 52, 1); // bootable
        CRC32 crc = new CRC32();
        crc.update(system.content);
        table.putInt(offset + 56, (int) crc.getValue());
    }

    private static int sectorsFor(int byteCount) {
        return (byteCount + SECTOR_SIZE - 1) / SECTOR_SIZE;
    }
}
