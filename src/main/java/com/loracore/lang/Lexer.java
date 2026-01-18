package com.loracore.lang;

import java.util.ArrayList;
import java.util.List;

public final class Lexer {
    private final String src;
    private int pos = 0;
    private int line = 1;

    public Lexer(String src) {
        this.src = src;
    }

    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }
            if (c == '\n') {
                out.add(new Token(TokenType.NEWLINE, "\\n", 0, line));
                advance();
                line++;
                continue;
            }
            if (isDigit(c)) {
                out.add(number());
                continue;
            }
            if (isAlpha(c)) {
                out.add(identifier());
                continue;
            }
            switch (c) {
                case '(' -> { out.add(simple(TokenType.LPAREN, "(")); advance(); }
                case ')' -> { out.add(simple(TokenType.RPAREN, ")")); advance(); }
                case ':' -> { out.add(simple(TokenType.COLON, ":")); advance(); }
                case '=' -> { out.add(simple(TokenType.EQUAL, "=")); advance(); }
                case '+' -> { out.add(simple(TokenType.PLUS, "+")); advance(); }
                case '-' -> { out.add(simple(TokenType.MINUS, "-")); advance(); }
                case '*' -> { out.add(simple(TokenType.STAR, "*")); advance(); }
                case '/' -> { out.add(simple(TokenType.SLASH, "/")); advance(); }
                default -> { advance(); }
            }
        }
        out.add(new Token(TokenType.EOF, "", 0, line));
        return out;
    }

    private Token simple(TokenType type, String lexeme) {
        return new Token(type, lexeme, 0, line);
    }

    private Token number() {
        int start = pos;
        while (!isAtEnd() && isDigit(peek())) advance();
        int value = Integer.parseInt(src.substring(start, pos));
        return new Token(TokenType.NUMBER, src.substring(start, pos), value, line);
    }

    private Token identifier() {
        int start = pos;
        while (!isAtEnd() && (isAlphaNumeric(peek()))) advance();
        String text = src.substring(start, pos);
        TokenType type = switch (text) {
            case "def" -> TokenType.DEF;
            case "print" -> TokenType.PRINT;
            default -> TokenType.IDENT;
        };
        return new Token(type, text, 0, line);
    }

    private boolean isAtEnd() {
        return pos >= src.length();
    }

    private char peek() {
        return src.charAt(pos);
    }

    private void advance() {
        pos++;
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}

