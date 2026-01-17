import lora.emulator.bus.SystemBus;
import lora.emulator.cpu.VirtualCpu;
import lora.emulator.memory.RamStick;
import lora.emulator.util.InstructionSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualCpuTest {

    private SystemBus bus;
    private VirtualCpu cpu;

    private static final int FLAG_ZERO = 1;
    private static final int FLAG_NEGATIVE = 2;

    @BeforeEach
    void setup() {
        bus = new SystemBus();
        bus.mapDevice(0x0000, new RamStick(4096, "Main RAM"));
        cpu = new VirtualCpu(bus, 1337L);
    }

    /**
     * Выполняет фиксированное количество инструкций.
     * Подходит для линейного кода. Для ветвлений лучше шагать вручную.
     */
    private void execute(int... instructions) {
        for (int i = 0; i < instructions.length; i++) {
            bus.writeInt(i * 4, instructions[i]);
        }
        cpu.pc = 0;
        for (int i = 0; i < instructions.length; i++) {
            cpu.step();
        }
    }

    @Test
    @DisplayName("ALU: Basic Arithmetic & Immediate Loading")
    void testMathBasics() {
        execute(
                InstructionSet.asm("LDI", 0, 0, 100),  // R0 = 100
                InstructionSet.asm("ADDI", 0, 0, 50),  // R0 = 100 + 50 = 150
                InstructionSet.asm("LDI", 1, 0, 10),   // R1 = 10
                InstructionSet.asm("SUB", 0, 1, 0)     // R0 = R0 - R1 (150 - 10) -> Accumulator style
        );

        assertEquals(140, cpu.registers[0], "SUB calculation failed");
        assertEquals(0, cpu.flags, "Flags shouldn't be set for positive result");
    }

    @Test
    @DisplayName("ALU: Negative Result & Flag Logic")
    void testNegativeFlags() {
        execute(
                InstructionSet.asm("LDI", 0, 0, 10),
                InstructionSet.asm("SUBI", 0, 0, 20) // R0 = 10 - 20 = -10
        );

        assertEquals(-10, cpu.registers[0]);
        assertTrue((cpu.flags & FLAG_NEGATIVE) != 0, "NEGATIVE flag must be set");
        assertEquals(0, cpu.flags & FLAG_ZERO, "ZERO flag must NOT be set");
    }

    @Test
    @DisplayName("ALU: Zero Flag Logic")
    void testZeroFlag() {
        execute(
                InstructionSet.asm("LDI", 0, 0, 555),
                InstructionSet.asm("SUBI", 0, 0, 555) // Result 0
        );

        assertEquals(0, cpu.registers[0]);
        assertTrue((cpu.flags & FLAG_ZERO) != 0, "ZERO flag must be set");
    }

    @Test
    @DisplayName("Flow Control: JMP")
    void testUnconditionalJump() {
        // Загружаем программу
        bus.writeInt(0, InstructionSet.asm("LDI", 0, 0, 1));
        bus.writeInt(4, InstructionSet.asm("JMP", 0, 0, 12)); // Прыжок на адрес 12
        bus.writeInt(8, InstructionSet.asm("LDI", 0, 0, 666)); // TRAP (не должно выполниться)
        bus.writeInt(12, InstructionSet.asm("LDI", 0, 0, 2));  // Target

        cpu.pc = 0;

        cpu.step(); // LDI -> PC=4
        assertEquals(4, cpu.pc);

        cpu.step(); // JMP -> PC=12
        assertEquals(12, cpu.pc, "JMP failed to update PC");

        cpu.step(); // LDI (Target) -> PC=16
        assertEquals(2, cpu.registers[0], "Executed instruction after JMP");
        assertEquals(16, cpu.pc);
    }

    @Test
    @DisplayName("Flow Control: CMP and Conditional Jumps (JG)")
    void testConditionalJumps() {
        // Ручное управление шагами для точности
        int[] prog = {
                InstructionSet.asm("LDI", 0, 0, 10),
                InstructionSet.asm("LDI", 1, 0, 20),
                InstructionSet.asm("CMP", 1, 0, 0),   // 20 - 10 > 0. Flags cleared (Positive).
                InstructionSet.asm("JG", 0, 0, 20),   // Если Greater (Positive), прыгаем на 20
                InstructionSet.asm("LDI", 5, 0, 666), // Trap (addr 16)
                InstructionSet.asm("LDI", 5, 0, 777)  // Target (addr 20)
        };

        for(int i=0; i<prog.length; i++) bus.writeInt(i*4, prog[i]);
        cpu.pc = 0;

        cpu.step(); // R0=10
        cpu.step(); // R1=20
        cpu.step(); // CMP
        cpu.step(); // JG -> Jump Taken -> PC=20

        assertEquals(20, cpu.pc, "JG did not jump");

        cpu.step(); // LDI R5, 777
        assertEquals(777, cpu.registers[5]);
    }

    @Test
    @DisplayName("Stack Operations: PUSH / POP")
    void testStack() {
        int initialSP = cpu.registers[15];

        execute(
                InstructionSet.asm("LDI", 0, 0, 0xAA),
                InstructionSet.asm("LDI", 1, 0, 0xBB),
                InstructionSet.asm("PUSH", 0, 0, 0), // Push AA
                InstructionSet.asm("PUSH", 1, 0, 0), // Push BB
                InstructionSet.asm("POP", 2, 0, 0),  // Pop -> BB
                InstructionSet.asm("POP", 3, 0, 0)   // Pop -> AA
        );

        assertEquals(0xBB, cpu.registers[2], "Stack LIFO failed (first pop)");
        assertEquals(0xAA, cpu.registers[3], "Stack LIFO failed (second pop)");
        assertEquals(initialSP, cpu.registers[15], "Stack Pointer leakage");
    }

    @Test
    @DisplayName("Function Call: CALL / RET")
    void testCallRet() {
        bus.writeInt(0, InstructionSet.asm("CALL", 0, 0, 8)); // Pushes 4, Jumps to 8
        bus.writeInt(4, InstructionSet.asm("HLT", 0, 0, 0));  // Return addr
        bus.writeInt(8, InstructionSet.asm("LDI", 0, 0, 42));
        bus.writeInt(12, InstructionSet.asm("RET", 0, 0, 0)); // Pops 4, Jumps to 4

        cpu.pc = 0;

        cpu.step(); // CALL
        assertEquals(8, cpu.pc);

        cpu.step(); // LDI
        assertEquals(42, cpu.registers[0]);

        cpu.step(); // RET
        assertEquals(4, cpu.pc, "RET did not return to correct address");
    }

    @Test
    @DisplayName("Memory: Little Endian ST / LD")
    void testMemoryWidth() {
        // Проверяем запись 16-битного числа (так как LDI 16-битный)
        // Но при записи 32-битного регистра в память через ST пишутся все 4 байта.
        // R1 = 0x00005678 (после LDI)
        execute(
                InstructionSet.asm("LDI", 0, 0, 0x100), // Addr
                InstructionSet.asm("LDI", 1, 0, 0x5678),// Value
                InstructionSet.asm("ST", 0, 1, 0)       // Store R1 to [R0]
        );

        // Проверяем Little Endian на шине
        // 0x5678 -> 78 56 00 00
        assertEquals(0x78, bus.readByte(0x100) & 0xFF, "Byte 0 mismatch");
        assertEquals(0x56, bus.readByte(0x101) & 0xFF, "Byte 1 mismatch");
        assertEquals(0x00, bus.readByte(0x102) & 0xFF, "Byte 2 mismatch (Upper bits should be 0)");
        assertEquals(0x00, bus.readByte(0x103) & 0xFF, "Byte 3 mismatch");

        // Проверяем чтение процессором обратно
        cpu.pc = 0;
        bus.writeInt(0, InstructionSet.asm("LDI", 0, 0, 0x100));
        bus.writeInt(4, InstructionSet.asm("LD", 2, 0, 0)); // R2 = [R0]

        cpu.step();
        cpu.step();

        assertEquals(0x5678, cpu.registers[2], "LD instruction failed");
    }

    @Test
    @DisplayName("Edge Case: Division by Zero")
    void testDivZero() {
        // Важно: Порядок аргументов ASM(Op, Dest, Src, Imm)
        // DIV R0, R1 -> Dest=0, Src=1.
        execute(
                InstructionSet.asm("LDI", 0, 0, 100),
                InstructionSet.asm("LDI", 1, 0, 0),
                InstructionSet.asm("DIV", 0, 1, 0) // R0 = R0 / R1
        );

        assertEquals(1, cpu.errorCount, "CPU should detect division by zero");
    }
}