package com.loracore.lang;

public final class Token {
    public final TokenType type;
    public final String lexeme;
    public final int intValue;
    public final int line;

    public Token(TokenType type, String lexeme, int intValue, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.intValue = intValue;
        this.line = line;
    }
}

