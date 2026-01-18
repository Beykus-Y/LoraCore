package com.loracore.lang;

import com.loracore.lang.Assembler;
import com.loracore.computer.InstructionSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Compiler {
    private final Map<String, Integer> varAddrs = new HashMap<>();
    private int nextVarAddr = 0x0400;

    public byte[] compile(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        AST.Program program = parser.parseProgram();
        Assembler asm = new Assembler();
        emitProgram(program, asm);
        asm.hlt();
        return asm.toByteArray();
    }

    private void emitProgram(AST.Program program, Assembler asm) {
        for (AST.Stmt s : program.statements) {
            emitStmt(s, asm);
        }
    }

    private void emitStmt(AST.Stmt s, Assembler asm) {
        if (s instanceof AST.Assign a) {
            emitExpr(a.value, asm);
            int addr = getVarAddr(a.name);
            asm.ldi(2, addr);
            asm.st(2, 0);
            return;
        }
        if (s instanceof AST.Print p) {
            emitExpr(p.value, asm);
            asm.mov(7, 0);
            asm.dump();
            return;
        }
    }

    private void emitExpr(AST.Expr e, Assembler asm) {
        if (e instanceof AST.Number n) {
            asm.ldi(0, n.value);
            return;
        }
        if (e instanceof AST.Var v) {
            int addr = getVarAddr(v.name);
            asm.ldi(2, addr);
            asm.ld(0, 2);
            return;
        }
        if (e instanceof AST.Binary b) {
            emitExpr(b.left, asm);
            emitToReg(b.right, 3, asm);
            switch (b.op) {
                case "+" -> asm.add(0, 3);
                case "-" -> asm.sub(0, 3);
                case "*" -> asm.mul(0, 3);
                case "/" -> asm.div(0, 3);
                default -> {}
            }
            return;
        }
        asm.ldi(0, 0);
    }

    private void emitToReg(AST.Expr e, int reg, Assembler asm) {
        if (e instanceof AST.Number n) {
            asm.ldi(reg, n.value);
            return;
        }
        if (e instanceof AST.Var v) {
            int addr = getVarAddr(v.name);
            asm.ldi(2, addr);
            asm.ld(reg, 2);
            return;
        }
        if (e instanceof AST.Binary b) {
            emitExpr(b.left, asm);
            emitToReg(b.right, 3, asm);
            asm.mov(reg, 0);
            switch (b.op) {
                case "+" -> asm.add(reg, 3);
                case "-" -> asm.sub(reg, 3);
                case "*" -> asm.mul(reg, 3);
                case "/" -> asm.div(reg, 3);
                default -> {}
            }
            return;
        }
        asm.ldi(reg, 0);
    }

    private int getVarAddr(String name) {
        Integer addr = varAddrs.get(name);
        if (addr == null) {
            addr = nextVarAddr;
            nextVarAddr += 4;
            varAddrs.put(name, addr);
        }
        return addr;
    }
}
