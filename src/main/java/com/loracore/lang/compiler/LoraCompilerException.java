package com.loracore.lang.compiler;

public class LoraCompilerException extends RuntimeException {
    private final int line;
    private final String sourceLine;

    public LoraCompilerException(String message, int line, String sourceLine) {
        super(message);
        this.line = line;
        this.sourceLine = sourceLine;
    }

    public LoraCompilerException(String message, int line, String sourceLine, Throwable cause) {
        super(message, cause);
        this.line = line;
        this.sourceLine = sourceLine;
    }

    @Override
    public String getMessage() {
        return String.format("Error at line %d: %s\n> %s", line, super.getMessage(), sourceLine.trim());
    }
}