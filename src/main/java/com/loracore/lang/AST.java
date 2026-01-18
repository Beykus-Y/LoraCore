package com.loracore.lang;

import java.util.List;

public final class AST {
    public static final class Program {
        public final List<Stmt> statements;
        public Program(List<Stmt> statements) { this.statements = statements; }
    }

    public static abstract class Stmt {}

    public static final class Assign extends Stmt {
        public final String name;
        public final Expr value;
        public Assign(String name, Expr value) { this.name = name; this.value = value; }
    }

    public static final class Print extends Stmt {
        public final Expr value;
        public Print(Expr value) { this.value = value; }
    }

    public static abstract class Expr {}

    public static final class Number extends Expr {
        public final int value;
        public Number(int value) { this.value = value; }
    }

    public static final class Var extends Expr {
        public final String name;
        public Var(String name) { this.name = name; }
    }

    public static final class Binary extends Expr {
        public final Expr left;
        public final String op;
        public final Expr right;
        public Binary(Expr left, String op, Expr right) { this.left = left; this.op = op; this.right = right; }
    }
}

