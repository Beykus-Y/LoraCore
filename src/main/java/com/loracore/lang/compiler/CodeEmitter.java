package com.loracore.lang.compiler;

public class CodeEmitter {
    private final StringBuilder asm = new StringBuilder();
    private final StringBuilder data = new StringBuilder();

    public void emit(String instruction) {
        asm.append(instruction).append("\n");
    }

    public void emit(String op, String... args) {
        asm.append(op);
        for (int i = 0; i < args.length; i++) {
            asm.append(i == 0 ? " " : ", ").append(args[i]);
        }
        asm.append("\n");
    }

    public void emitLabel(String label) {
        asm.append(label).append(":\n");
    }

    public void emitData(String label, int value) {
        if (label != null) {
            data.append(label).append(":\n");
        }
        data.append(".data ").append(value).append("\n");
    }

    public void emitDataValue(int value) {
        // data - это StringBuilder для секции данных
        // Если у тебя нет доступа, сделай data protected или добавь метод
        // Допустим, data доступна:
        // data.append(".data ").append(value).append("\n");
        // Лучше сделай так:
        emitData(null, value); // Перегрузи emitData
    }

    public void emitComment(String comment) {
        asm.append("; ").append(comment).append("\n");
    }

    public void emitJump(String op, String label) {
        asm.append(op).append(" ").append(label).append("\n");
    }

    public String build() {
        return asm.toString() + data.toString();
    }

    public void emitDataWithoutLabel(int value) {
        // .data VALUE
        // Используем StringBuilder data, который у тебя уже есть
        // Внимание: проверь доступ к полю data, оно может быть private.
        // Если data private, добавь метод:
        emitRawData(".data " + value);
    }

    private void emitRawData(String str) {
        // append to data builder
    }
}