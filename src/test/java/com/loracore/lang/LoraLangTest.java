package com.loracore.lang;

import com.loracore.computer.*;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LoraLangTest {

    private static int encode(int opcode, int rD, int rS, int imm16) {
        return (opcode & 0xFF) << 24
                | (rD & 0x0F) << 20
                | (rS & 0x0F) << 16
                | (imm16 & 0xFFFF);
    }

    private VirtualCpu createCpu() {
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        return new VirtualCpu(bus, ram, config, 42L);
    }

    @Test
    public void testIndentDedentTokens() {
        String src = ""
                + "def main:\n"
                + "    a = 1\n"
                + "    if a < 2:\n"
                + "        a = a + 1\n"
                + "    while a < 5:\n"
                + "        a = a + 1\n";
        LoraLexer lx = new LoraLexer(src);
        List<LoraToken> toks = lx.tokenize();
        long indentCount = toks.stream().filter(t -> t.type == LoraTokenType.INDENT).count();
        long dedentCount = toks.stream().filter(t -> t.type == LoraTokenType.DEDENT).count();
        assertTrue(indentCount >= 2);
        assertTrue(dedentCount >= 2);
    }

    @Test
    public void testParseAndCompileWhile() {
        String src = ""
                + "def main:\n"
                + "    a = 1\n"
                + "    while a < 3:\n"
                + "        a = a + 1\n";
        LoraLexer lx = new LoraLexer(src);
        LoraAst.Parser parser = new LoraAst.Parser(lx.tokenize());
        LoraAst.Program prog = parser.parseProgram();
        assertFalse(prog.statements.isEmpty());
        assertTrue(prog.statements.get(0) instanceof LoraAst.Def);
        LoraAst.Compiler compiler = new LoraAst.Compiler();
        LoraAst.CpuProgram cpuProg = compiler.compile(prog);
        runCpuAndAssertRegister(cpuProg, 0x100, "main", 3, 0);
    }

    @Test
    public void testIfElse() {
        String src = ""
                + "def main:\n"
                + "    a = 2\n"
                + "    b = 1\n"
                + "    if a < b:\n"
                + "        a = a + 1\n"
                + "    else:\n"
                + "        a = a - 1\n";
        LoraLexer lx = new LoraLexer(src);
        LoraAst.Parser parser = new LoraAst.Parser(lx.tokenize());
        LoraAst.Program prog = parser.parseProgram();
        LoraAst.Compiler compiler = new LoraAst.Compiler();
        LoraAst.CpuProgram cpuProg = compiler.compile(prog);
        runCpuAndAssertRegister(cpuProg, 0x200, "main", 1, 0);
    }

    @Test
    public void testArithmeticAndEq() {
        String src = ""
                + "def main:\n"
                + "    a = 1 + 2\n"
                + "    if a == 3:\n"
                + "        a = a + 1\n";
        LoraLexer lx = new LoraLexer(src);
        LoraAst.Parser parser = new LoraAst.Parser(lx.tokenize());
        LoraAst.Program prog = parser.parseProgram();
        LoraAst.Compiler compiler = new LoraAst.Compiler();
        LoraAst.CpuProgram cpuProg = compiler.compile(prog);
        runCpuAndAssertRegister(cpuProg, 0x300, "main", 4, 0);
    }

    private void runCpuAndAssertRegister(LoraAst.CpuProgram cpuProg, int base, String entryName, int expectedValue, int regIndex) {
        VirtualCpu cpu = createCpu();
        SystemBus bus = new SystemBus();
        GenericRam ram = new GenericRam(0x200000);
        bus.mapDevice(0x000000, ram);
        CpuTiers.Config config = new CpuTiers.Config("test", 1.0, 100_000, 200_000);
        cpu = new VirtualCpu(bus, ram, config, 99L);

        int[] code = cpuProg.code;
        for (int i = 0; i < code.length; i++) {
            bus.writeInt(i * 4, code[i]);
        }
        Map<String, Integer> functions = cpuProg.functions;
        int entryPc = 0;
        if (functions != null && functions.containsKey(entryName)) {
            entryPc = functions.get(entryName);
        }

        int sp = cpu.registers[15];
        bus.writeInt(sp - 4, 4);
        cpu.registers[15] = sp - 4;
        cpu.pc = entryPc;
        for (int steps = 0; steps < 1000; steps++) {
            int spent = cpu.step();
            if (cpu.pc == 4) break;
            if (spent <= 0) break;
        }
        assertEquals(expectedValue, cpu.registers[regIndex]);
        assertEquals(4, cpu.pc);
    }
}
