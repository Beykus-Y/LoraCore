package com.loracore.computer;

import com.loracore.computer.device.ConfigurationSpaceDevice;
import com.loracore.computer.device.KeyboardDevice;
import com.loracore.computer.pnp.PnpEntry;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public class BootFlowTest {

    @Test
    public void packagedBiosAndDiskBootWithoutInvalidMemoryAccess() throws Exception {
        byte[] bios = readResource("/assets/loracore/os/bios.bin");
        byte[] disk = readResource("/assets/loracore/os/disk.bin");

        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(512 * 1024);
        bus.mapDevice(0, ram);
        bus.mapDevice(0x300000, new SynchronousDisk(bus, disk));
        KeyboardDevice keyboard = new KeyboardDevice(bus);
        bus.mapDevice(0x310000, keyboard);
        int gpuSize = GpuMmioDevice.VRAM_SIZE_BYTES + 64;
        RecordingDevice gpu = new RecordingDevice(gpuSize);
        bus.mapDevice(0x400000, gpu);
        bus.mapDevice(0xFFF000, new ConfigurationSpaceDevice(List.of(
                new PnpEntry(PnpEntry.TYPE_RAM, 0, ram.getSize(), 0),
                new PnpEntry(PnpEntry.TYPE_STORAGE, 0x300000, 64, 0),
                new PnpEntry(PnpEntry.TYPE_INPUT, 0x310000, 64, 0),
                new PnpEntry(PnpEntry.TYPE_GPU, 0x400000, gpuSize, 0)
        )));

        for (int i = 0; i < bios.length; i++) {
            ram.write(i, bios[i]);
        }

        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 1234L);

        try {
            for (int i = 0; i < 500_000; i++) {
                cpu.step();
            }
            assertTrue("Kernel never sent commands to the GPU MMIO registers",
                    gpu.registerWriteCount > 8);
            assertTrue("LoraFS was not formatted on first boot",
                    ByteBuffer.wrap(disk).order(ByteOrder.LITTLE_ENDIAN).getInt(64 * 512)
                            == 0x3153464C);

            gpu.commands.clear();
            keyboard.pushKey('A');
            keyboard.pushKey(8);
            keyboard.pushKey(13);
            for (int i = 0; i < 50_000; i++) {
                cpu.step();
            }

            assertTrue("Enter was rendered as a glyph",
                    gpu.commands.stream().noneMatch(command -> command.type() == 3 && command.arg2() == 13));
            assertTrue("Character was not drawn at the shell prompt",
                    gpu.commands.stream().anyMatch(command -> command.type() == 3
                            && command.arg2() == 'A' && command.x() == 58 && command.y() == 80));
            assertTrue("Backspace did not erase the preceding character cell",
                    gpu.commands.stream().anyMatch(command -> command.type() == 2
                            && command.x() == 58 && command.y() == 80
                            && command.arg2() == 8 && command.arg3() == 16));

            pushText(keyboard, "write note");
            keyboard.pushKey(13);
            pushText(keyboard, "hello");
            keyboard.pushKey(13);
            for (int i = 0; i < 200_000; i++) {
                cpu.step();
            }
            ByteBuffer persisted = ByteBuffer.wrap(disk).order(ByteOrder.LITTLE_ENDIAN);
            assertTrue("LoraFS did not persist the note length",
                    persisted.getInt(64 * 512 + 72) == 5);
            assertTrue("LoraFS did not persist note contents",
                    persisted.getInt(65 * 512) == 'h'
                            && persisted.getInt(65 * 512 + 16) == 'o');
        } catch (HardwareInterruptException e) {
            fail(String.format("Boot failed at PC=0x%08X SP=0x%08X: %s",
                    cpu.pc, cpu.registers[15], e.getMessage()));
        }
    }

    private static void pushText(KeyboardDevice keyboard, String text) {
        for (int i = 0; i < text.length(); i++) {
            keyboard.pushKey(text.charAt(i));
        }
    }

    @Test
    public void dualbootCanStartRecoverySlot() throws Exception {
        byte[] bios = readResource("/assets/loracore/os/bios.bin");
        byte[] disk = readResource("/assets/loracore/os/disk.bin");

        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(512 * 1024);
        bus.mapDevice(0, ram);
        bus.mapDevice(0x300000, new SynchronousDisk(bus, disk));
        KeyboardDevice keyboard = new KeyboardDevice(bus);
        keyboard.pushKey('2');
        bus.mapDevice(0x310000, keyboard);
        int gpuSize = GpuMmioDevice.VRAM_SIZE_BYTES + 64;
        RecordingDevice gpu = new RecordingDevice(gpuSize);
        bus.mapDevice(0x400000, gpu);
        bus.mapDevice(0xFFF000, new ConfigurationSpaceDevice(List.of(
                new PnpEntry(PnpEntry.TYPE_RAM, 0, ram.getSize(), 0),
                new PnpEntry(PnpEntry.TYPE_STORAGE, 0x300000, 64, 0),
                new PnpEntry(PnpEntry.TYPE_INPUT, 0x310000, 64, 0),
                new PnpEntry(PnpEntry.TYPE_GPU, 0x400000, gpuSize, 0)
        )));
        for (int i = 0; i < bios.length; i++) {
            ram.write(i, bios[i]);
        }

        VirtualCpu cpu = new VirtualCpu(bus, ram,
                new CpuTiers.Config("test", 1.0, 100_000, 200_000), 4321L);
        try {
            for (int i = 0; i < 500_000; i++) {
                cpu.step();
            }
            assertTrue("Recovery slot did not start; rendered text was: " + gpu.renderedText(),
                    gpu.renderedText().contains("Recovery environment is running"));
        } catch (HardwareInterruptException e) {
            fail(String.format("Recovery boot failed at PC=0x%08X SP=0x%08X: %s",
                    cpu.pc, cpu.registers[15], e.getMessage()));
        }
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream input = BootFlowTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return input.readAllBytes();
        }
    }

    private static class ByteArrayDevice implements IMemoryMappedDevice {
        private final byte[] data;

        private ByteArrayDevice(int size) {
            data = new byte[size];
        }

        @Override public int getSize() { return data.length; }
        @Override public byte read(int offset) { return data[offset]; }
        @Override public void write(int offset, byte value) { data[offset] = value; }
    }

    private static final class SynchronousDisk extends ByteArrayDevice {
        private final SystemBus bus;
        private final byte[] image;
        private int lba;
        private int count = 1;
        private int dmaAddress;

        private SynchronousDisk(SystemBus bus, byte[] image) {
            super(64);
            this.bus = bus;
            this.image = image;
        }

        @Override
        public int readInt(int offset) {
            return offset == 0 ? 0 : super.readInt(offset);
        }

        @Override
        public void writeInt(int offset, int value) {
            switch (offset) {
                case 0 -> execute(value);
                case 8 -> lba = value;
                case 12 -> count = value;
                case 16 -> dmaAddress = value;
                default -> super.writeInt(offset, value);
            }
        }

        private void execute(int command) {
            if (command == 1) {
                readSectors();
            } else if (command == 2) {
                writeSectors();
            }
        }

        private void readSectors() {
            int source = lba * 512;
            int length = count * 512;
            for (int i = 0; i < length; i++) {
                byte value = source + i < image.length ? image[source + i] : 0;
                bus.writeByte(dmaAddress + i, value);
            }
        }

        private void writeSectors() {
            int target = lba * 512;
            int length = count * 512;
            for (int i = 0; i < length && target + i < image.length; i++) {
                image[target + i] = bus.readByte(dmaAddress + i);
            }
        }
    }

    private static final class RecordingDevice extends ByteArrayDevice {
        private final int registersOffset;
        private int registerWriteCount;
        private final List<GpuCommand> commands = new ArrayList<>();

        private RecordingDevice(int size) {
            super(size);
            registersOffset = size - 64;
        }

        @Override
        public void write(int offset, byte value) {
            super.write(offset, value);
            if (offset >= registersOffset) {
                registerWriteCount++;
            }
        }

        @Override
        public void writeInt(int offset, int value) {
            super.writeInt(offset, value);
            if (offset == registersOffset + 4 && (value == 2 || value == 3)) {
                commands.add(new GpuCommand(
                        value,
                        readInt(registersOffset + 8),
                        readInt(registersOffset + 12),
                        readInt(registersOffset + 16),
                        readInt(registersOffset + 20)
                ));
            }
        }

        private String renderedText() {
            StringBuilder text = new StringBuilder();
            for (GpuCommand command : commands) {
                if (command.type() == 3 && Character.isValidCodePoint(command.arg2())) {
                    text.appendCodePoint(command.arg2());
                }
            }
            return text.toString();
        }
    }

    private record GpuCommand(int type, int x, int y, int arg2, int arg3) {}
}
