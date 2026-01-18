package com.loracore.lang;

import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public AST.Program parseProgram() {
        expect(TokenType.DEF);
        expectIdent("main");
        expect(TokenType.LPAREN);
        expect(TokenType.RPAREN);
        if (match(TokenType.COLON)) {}
        consumeNewlines();
        List<AST.Stmt> stmts = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            if (match(TokenType.NEWLINE)) continue;
            stmts.add(parseStmt());
            consumeNewlines();
        }
        return new AST.Program(stmts);
    }

    private AST.Stmt parseStmt() {
        if (check(TokenType.PRINT)) {
            advance();
            expect(TokenType.LPAREN);
            AST.Expr e = parseExpr();
            expect(TokenType.RPAREN);
            return new AST.Print(e);
        }
        String name = expect(TokenType.IDENT).lexeme;
        expect(TokenType.EQUAL);
        AST.Expr e = parseExpr();
        return new AST.Assign(name, e);
    }

    private AST.Expr parseExpr() {
        AST.Expr left = parseTerm();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = advance().lexeme;
            AST.Expr right = parseTerm();
            left = new AST.Binary(left, op, right);
        }
        return left;
    }

    private AST.Expr parseTerm() {
        AST.Expr left = parseFactor();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            String op = advance().lexeme;
            AST.Expr right = parseFactor();
            left = new AST.Binary(left, op, right);
        }
        return left;
    }

    private AST.Expr parseFactor() {
        if (check(TokenType.NUMBER)) {
            int v = advance().intValue;
            return new AST.Number(v);
        }
        if (check(TokenType.IDENT)) {
            String name = advance().lexeme;
            return new AST.Var(name);
        }
        if (check(TokenType.LPAREN)) {
            advance();
            AST.Expr e = parseExpr();
            expect(TokenType.RPAREN);
            return e;
        }
        return new AST.Number(0);
    }

    private void consumeNewlines() {
        while (check(TokenType.NEWLINE)) advance();
    }

    private Token expect(TokenType type) {
        Token t = advance();
        return t;
    }

    private void expectIdent(String name) {
        Token t = expect(TokenType.IDENT);
    }

    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }

    private boolean check(TokenType type) {
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) pos++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }
}

