package lora.emulator;

import lora.emulator.bus.IMemoryMappedDevice;
import lora.emulator.bus.ITickable;
import lora.emulator.bus.SystemBus;
import lora.emulator.cpu.VirtualCpu;
import lora.emulator.runtime.SystemRunner;

public class LoraComputer {

    private final SystemBus bus;
    private final VirtualCpu cpu;
    private Thread systemThread;
    private SystemRunner systemRunner;
    private ITickable[] tickables = new ITickable[0];

    // --- КАРТА ПАМЯТИ (Motherboard Layout v2.1) ---

    public static final int ADDR_RAM_MAIN    = 0x000000;
    public static final int ADDR_ROM_FONT    = 0x002000;
    public static final int ADDR_GPU_VRAM    = 0x004000;

    // MMIO Base: 0xF00000
    public static final int MMIO_BASE        = 0xF00000;

    public static final int ADDR_KEYBOARD    = MMIO_BASE + 0x0000; // 0xF00000 (Size: 16)
    public static final int ADDR_DISK_CTRL   = MMIO_BASE + 0x1000; // 0xF01000 (Size: 32)

    // NIC занимает 0x1100 байт (до 0xF03100), поэтому даем ему запас
    public static final int ADDR_NIC         = MMIO_BASE + 0x2000; // 0xF02000

    // Сдвигаем CMOS дальше, чтобы не было конфликта с NIC
    public static final int ADDR_CMOS        = MMIO_BASE + 0x4000; // 0xF04000 (Было 3000 - конфликт)

    public LoraComputer(CpuTiers.Config cpuConfig, long siliconSeed) {
        this.bus = new SystemBus();
        this.cpu = new VirtualCpu(bus, siliconSeed);
        this.cpu.voltageMV = (int)(cpuConfig.voltage * 1000);
        this.cpu.freqHz = (int)cpuConfig.targetFreq;
    }

    public void tickPeripherals(long cycles) {
        for (int i = 0; i < tickables.length; i++) {
            tickables[i].tick(cycles);
        }
    }

    public void mapDevice(int address, IMemoryMappedDevice device) {
        bus.mapDevice(address, device);
        if (device instanceof ITickable) {
            ITickable[] newTickables = new ITickable[tickables.length + 1];
            System.arraycopy(tickables, 0, newTickables, 0, tickables.length);
            newTickables[tickables.length] = (ITickable) device;
            this.tickables = newTickables;
        }
    }

    public void flashMemory(int startAddress, byte[] binaryImage) {
        for (int i = 0; i < binaryImage.length; i++) {
            bus.writeByte(startAddress + i, binaryImage[i]);
        }
        System.out.printf("[Flash] Written %d bytes to 0x%06X\n", binaryImage.length, startAddress);
    }

    public void start() {
        if (systemThread == null || !systemThread.isAlive()) {
            systemRunner = new SystemRunner(this, cpu.freqHz);
            systemThread = new Thread(systemRunner, "Lora-Clock");
            systemThread.setPriority(Thread.MAX_PRIORITY);
            systemThread.start();
        }
    }

    public void stop() {
        if (systemRunner != null) systemRunner.stop();
    }

    public VirtualCpu getCpu() { return cpu; }
    public double getTemperature() { return cpu.tempmC / 1000.0; }
    public long getErrorCount() { return cpu.errorCount; }
    public SystemBus getBus() { return bus; }
}