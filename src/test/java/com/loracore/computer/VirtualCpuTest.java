package com.loracore.computer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VirtualCpuTest {

    private VirtualCpu createCpu() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        return new VirtualCpu(bus, ram, config, 1234L);
    }

    private static int encode(int opcode, int rD, int rS, int imm16) {
        return (opcode & 0xFF) << 24
                | (rD & 0x0F) << 20
                | (rS & 0x0F) << 16
                | (imm16 & 0xFFFF);
    }

    @Test
    public void testAddAndFlags() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 1L);

        cpu.registers[0] = 10;
        cpu.registers[1] = -10;

        int instrAdd = encode(InstructionSet.OP_ADD, 0, 1, 0);
        bus.writeInt(0, instrAdd);
        cpu.pc = 0;

        int spent = cpu.step();
        assertEquals(1, spent);
        assertEquals(0, cpu.registers[0]);
        assertTrue((cpu.flags & 0x01) != 0);
    }

    @Test
    public void testLoadStoreAndStack() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 2L);

        cpu.registers[1] = 0x100;
        cpu.registers[2] = 0x12345678;

        int st = encode(InstructionSet.OP_ST, 1, 2, 0);
        int ld = encode(InstructionSet.OP_LD, 3, 1, 0);
        int push = encode(InstructionSet.OP_PUSH, 0, 2, 0);
        int pop = encode(InstructionSet.OP_POP, 4, 0, 0);

        bus.writeInt(0, st);
        bus.writeInt(4, ld);
        bus.writeInt(8, push);
        bus.writeInt(12, pop);

        cpu.pc = 0;
        cpu.step();
        cpu.step();
        cpu.step();
        cpu.step();

        assertEquals(0x12345678, cpu.registers[3]);
        assertEquals(0x12345678, cpu.registers[4]);
    }

    @Test
    public void testCmpAndJumps() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 3L);

        cpu.registers[0] = 5;
        cpu.registers[1] = 5;

        int cmp = encode(InstructionSet.OP_CMP, 0, 1, 0);
        int jz = encode(InstructionSet.OP_JZ, 0, 0, 16);

        bus.writeInt(0, cmp);
        bus.writeInt(4, jz);

        cpu.pc = 0;
        cpu.step();
        cpu.step();

        assertEquals(16, cpu.pc);
    }

    @Test
    public void testCallAndRet() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 4L);

        int call = encode(InstructionSet.OP_CALL, 0, 0, 16);
        int hlt = encode(InstructionSet.OP_HLT, 0, 0, 0);
        int ret = encode(InstructionSet.OP_RET, 0, 0, 0);

        bus.writeInt(0, call);
        bus.writeInt(4, hlt);
        bus.writeInt(16, ret);

        cpu.pc = 0;
        cpu.step();
        assertEquals(16, cpu.pc);

        cpu.step();
        assertEquals(4, cpu.pc);
    }

    @Test
    public void testDivAndMod() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 5L);

        cpu.registers[0] = 20;
        cpu.registers[1] = 6;
        cpu.registers[2] = 20;

        int div = encode(InstructionSet.OP_DIV, 0, 1, 0);
        int mod = encode(InstructionSet.OP_MOD, 2, 1, 0);

        bus.writeInt(0, div);
        bus.writeInt(4, mod);

        cpu.pc = 0;
        cpu.step();
        cpu.step();

        assertEquals(3, cpu.registers[0]);
        assertEquals(20 % 6, cpu.registers[2]);
    }

    @Test
    public void testBitwiseAndShiftFlags() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 6L);

        cpu.registers[0] = 0b1010;
        cpu.registers[1] = 0b1100;
        cpu.registers[2] = 0b1010;
        cpu.registers[3] = 0b1010;
        cpu.registers[4] = 0b1010;

        int and = encode(InstructionSet.OP_AND, 2, 1, 0);
        int or = encode(InstructionSet.OP_OR, 3, 1, 0);
        int xor = encode(InstructionSet.OP_XOR, 4, 1, 0);
        int not = encode(InstructionSet.OP_NOT, 0, 0, 0);
        int shl = encode(InstructionSet.OP_SHL, 1, 0, 1);
        int shr = encode(InstructionSet.OP_SHR, 2, 0, 1);

        bus.writeInt(0, and);
        bus.writeInt(4, or);
        bus.writeInt(8, xor);
        bus.writeInt(12, not);
        bus.writeInt(16, shl);
        bus.writeInt(20, shr);

        cpu.pc = 0;
        cpu.step();
        cpu.step();
        cpu.step();
        cpu.step();
        cpu.step();
        cpu.step();

        assertEquals(0b0100, cpu.registers[2]);
        assertEquals(0b1110, cpu.registers[3]);
        assertEquals(0b0110, cpu.registers[4]);
        assertEquals(0, cpu.flags & 0x03);
    }

    @Test
    public void testWaitInstructionConsumesCycles() {
        VirtualCpu cpu = createCpu();
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        cpu = new VirtualCpu(bus, ram, config, 7L);

        int wait = encode(InstructionSet.OP_WAIT, 0, 0, 10);
        bus.writeInt(0, wait);

        cpu.pc = 0;
        int spent = cpu.step();

        assertEquals(10, spent);
    }

    @Test
    public void testGenericRamIsFinite() {
        GenericRam ram = new GenericRam(4096);
        assertEquals(4096, ram.getSize());

        ram.write(0, (byte) 1);
        ram.write(4095, (byte) 2);
        assertEquals(1, ram.read(0));
        assertEquals(2, ram.read(4095));
        assertEquals(0, ram.read(4096));
    }

    @Test(expected = HardwareInterruptException.class)
    public void testSystemBusOutOfGlobalBoundsThrows() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(1024);
        bus.mapDevice(0x000000, ram);
        bus.readByte(0x1000000);
    }

    @Test
    public void testResetClearsInterruptAndRuntimeStateBeforeReboot() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 8L);

        bus.writeInt(0, encode(InstructionSet.OP_STI, 0, 0, 0));
        cpu.step();
        bus.requestInterrupt(3);

        cpu.reset();

        assertEquals(0, cpu.pc);
        assertEquals(0, cpu.totalCycles);
        assertEquals(-1, bus.checkPendingInterrupts());
        assertEquals(0x0FFC, cpu.registers[15]);

        bus.writeInt(0, encode(InstructionSet.OP_NOP, 0, 0, 0));
        cpu.step();
        assertEquals(4, cpu.pc);
        assertEquals(1, cpu.totalCycles);
        assertEquals(0x0FFC, cpu.registers[15]);
    }

    @Test
    public void testPagedLoadAndStoreAcrossNonContiguousFrames() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x50000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 9L);

        int pageTable = 0x4000;
        bus.writeInt(pageTable, 0x10000 | 1);
        bus.writeInt(pageTable + 4, 0x30000 | 1);
        bus.writeInt(0x10000, encode(InstructionSet.OP_LD, 2, 1, 0));
        bus.writeInt(0x10004, encode(InstructionSet.OP_ST, 1, 2, 0));

        bus.writeByte(0x10FFF, (byte) 0x78);
        bus.writeByte(0x30000, (byte) 0x56);
        bus.writeByte(0x30001, (byte) 0x34);
        bus.writeByte(0x30002, (byte) 0x12);

        cpu.cr3 = pageTable;
        cpu.pagingEnabled = true;
        cpu.registers[1] = 0x0FFF;

        cpu.step();
        assertEquals(0x12345678, cpu.registers[2]);

        cpu.registers[2] = 0x89ABCDEF;
        cpu.step();
        assertEquals(0xEF, bus.readByte(0x10FFF) & 0xFF);
        assertEquals(0xCD, bus.readByte(0x30000) & 0xFF);
        assertEquals(0xAB, bus.readByte(0x30001) & 0xFF);
        assertEquals(0x89, bus.readByte(0x30002) & 0xFF);
    }

    @Test
    public void testInstructionFetchAcrossNonContiguousFrames() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x50000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        VirtualCpu cpu = new VirtualCpu(bus, ram, config, 10L);

        int pageTable = 0x4000;
        bus.writeInt(pageTable, 0x10000 | 1);
        bus.writeInt(pageTable + 4, 0x30000 | 1);
        int instruction = encode(InstructionSet.OP_LDI, 4, 0, 0xBEEF);
        bus.writeByte(0x10FFF, (byte) instruction);
        bus.writeByte(0x30000, (byte) (instruction >>> 8));
        bus.writeByte(0x30001, (byte) (instruction >>> 16));
        bus.writeByte(0x30002, (byte) (instruction >>> 24));

        cpu.cr3 = pageTable;
        cpu.pagingEnabled = true;
        cpu.pc = 0x0FFF;

        cpu.step();
        assertEquals(0xBEEF, cpu.registers[4]);
        assertEquals(0x1003, cpu.pc);
    }
}

