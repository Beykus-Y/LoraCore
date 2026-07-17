package com.loracore.computer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FactoryDiskInstallerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void legacyDiskIsBackedUpBeforeFreshFactoryImageIsInstalled() throws Exception {
        Path folder = temporaryFolder.newFolder("disk").toPath();
        Path diskPath = folder.resolve("disk.bin");
        byte[] legacy = new byte[8192];
        for (int i = 0; i < legacy.length; i++) {
            legacy[i] = (byte) (i * 31);
        }
        Files.write(diskPath, legacy);

        byte[] factory = currentFactoryImage(4096);
        RawDiskDrive drive = new RawDiskDrive(folder, "disk.bin", 16384);
        assertEquals(FactoryDiskInstaller.Result.MIGRATED,
                FactoryDiskInstaller.ensureInstalled(drive, diskPath, 16384, factory));

        assertArrayEquals(legacy, Files.readAllBytes(folder.resolve("disk.bin.pre-2.0.bak")));
        byte[] migrated = Files.readAllBytes(diskPath);
        assertEquals(16384, migrated.length);
        assertArrayEquals(factory, java.util.Arrays.copyOf(migrated, factory.length));
    }

    @Test
    public void currentDiskAndItsUserDataAreLeftUntouched() throws Exception {
        Path folder = temporaryFolder.newFolder("current").toPath();
        Path diskPath = folder.resolve("disk.bin");
        byte[] current = currentFactoryImage(8192);
        current[64 * 8] = 77;
        Files.write(diskPath, current);
        RawDiskDrive drive = new RawDiskDrive(folder, "disk.bin", current.length);

        assertEquals(FactoryDiskInstaller.Result.CURRENT,
                FactoryDiskInstaller.ensureInstalled(drive, diskPath, current.length,
                        currentFactoryImage(4096)));
        assertArrayEquals(current, Files.readAllBytes(diskPath));
        assertTrue(Files.notExists(folder.resolve("disk.bin.pre-2.0.bak")));
    }

    private static byte[] currentFactoryImage(int size) {
        byte[] image = new byte[size];
        ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(FactoryDiskInstaller.CONFIG_MAGIC_OFFSET,
                        FactoryDiskInstaller.CONFIG_MAGIC);
        return image;
    }
}
