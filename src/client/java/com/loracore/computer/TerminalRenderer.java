// Полный исправленный файл: src/client/java/com/loracore/computer/TerminalRenderer.java
package com.loracore.computer;

import com.loracore.LoraCoreClient;
import com.loracore.gui.CrashScreen;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Отвечает ИСКЛЮЧИТЕЛЬНО за состояние и отрисовку текстового терминала.
 * Является "драйвером" для TabletScreen, когда тот работает в Lua-режиме.
 */
public class TerminalRenderer implements Terminal {


    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int FONT_HEIGHT = 9;
    private static final int FONT_WIDTH = 6;

    private TerminalChar[][] buffer;
    private int termWidth, termHeight;
    private int cursorX = 1, cursorY = 1;
    private boolean cursorBlinkEnabled = true;
    private boolean cursorVisible = true;
    private int tickCounter = 0;
    private int currentTextColor = TEXT_COLOR;
    private int currentBgColor = 0xFF1E1E1E;

    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>(1);
    private String currentInputLine = "";

    private final Screen parentScreen;

    public TerminalRenderer(Screen parent) {
        this.parentScreen = parent;
    }

    public boolean resize(int tabletWidth, int tabletHeight) {
        int newTermWidth = (tabletWidth - 8) / FONT_WIDTH;
        int newTermHeight = (tabletHeight - 8) / FONT_HEIGHT;

        if (this.buffer == null || this.termWidth != newTermWidth || this.termHeight != newTermHeight) {
            this.termWidth = newTermWidth;
            this.termHeight = newTermHeight;

            this.buffer = new TerminalChar[termHeight][termWidth];
            for (int y = 0; y < termHeight; y++) {
                for (int x = 0; x < termWidth; x++) {
                    buffer[y][x] = new TerminalChar(' ', currentTextColor, currentBgColor);
                }
            }
            return true;
        }
        return false;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int tabletX, int tabletY) {
        if (cursorBlinkEnabled) {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                cursorVisible = !cursorVisible;
            }
        } else {
            cursorVisible = true;
        }

        if (buffer != null) {
            for (int y = 0; y < termHeight; y++) {
                for (int x = 0; x < termWidth; x++) {
                    TerminalChar ch = buffer[y][x];
                    int drawX = tabletX + x * FONT_WIDTH; // Убрали смещение 4, так как translate теперь в TabletScreen
                    int drawY = tabletY + y * FONT_HEIGHT;
                    context.fill(drawX, drawY, drawX + FONT_WIDTH, drawY + FONT_HEIGHT, ch.bgColor);
                    textRenderer.draw(String.valueOf(ch.character), (float)drawX, (float)drawY, ch.fgColor, false, context.getMatrices().peek().getPositionMatrix(), context.getVertexConsumers(), TextRenderer.TextLayerType.NORMAL, 0, 15728880);
                }
            }
        }

        if (cursorVisible) {
            int cursorDrawX = tabletX + (cursorX - 1) * FONT_WIDTH;
            int cursorDrawY = tabletY + (cursorY - 1) * FONT_HEIGHT;
            context.fill(cursorDrawX, cursorDrawY, cursorDrawX + FONT_WIDTH, cursorDrawY + FONT_HEIGHT, 0xFFFFFFFF);
        }
    }

    // --- Методы для обработки ввода из LuaRuntime ---
    public void onCharTyped(char chr) {
        this.currentInputLine += chr;
        this.print(String.valueOf(chr)); // Отображаем введенный символ
    }

    public void onEnterPressed() {
        try {
            this.inputQueue.put(this.currentInputLine);
            this.print("\n");
            this.currentInputLine = "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void onBackspacePressed() {
        if (!this.currentInputLine.isEmpty()) {
            this.currentInputLine = this.currentInputLine.substring(0, this.currentInputLine.length() - 1);
            if (this.cursorX > 1) {
                this.setCursorPos(this.cursorX - 1, this.cursorY);
                this.print(" ");
                this.setCursorPos(this.cursorX - 1, this.cursorY);
            } else if (this.cursorY > 1) {
                this.cursorY--;
                this.cursorX = this.termWidth + 1; // Устанавливаем в конец строки
                this.setCursorPos(this.cursorX - 1, this.cursorY);
                this.print(" ");
                this.setCursorPos(this.cursorX - 1, this.cursorY);
            }
        }
    }

    // --- Методы, реализующие интерфейс Terminal, для вызова из TabletScreen ---

    public void print(String text) {
        for (char ch : text.toCharArray()) {
            if (ch == '\n') {
                cursorX = 1;
                cursorY++;
            } else if (ch == '\r') {
                cursorX = 1;
            } else {
                if (cursorX > termWidth) {
                    cursorX = 1;
                    cursorY++;
                }
                if (cursorY > termHeight) {
                    scrollBuffer();
                    cursorY = termHeight;
                }
                if (buffer != null && (cursorY - 1) >= 0 && (cursorY - 1) < buffer.length && (cursorX - 1) >= 0 && (cursorX - 1) < buffer[0].length) {
                    buffer[cursorY - 1][cursorX - 1].setCharacter(ch, currentTextColor, currentBgColor);
                    cursorX++;
                }
            }
        }
        resetCursorBlink();
    }

    public void clear() {
        if (buffer != null) {
            for (int y = 0; y < termHeight; y++) {
                for (int x = 0; x < termWidth; x++) {
                    buffer[y][x].setCharacter(' ', currentTextColor, currentBgColor);
                }
            }
        }
        this.cursorX = 1;
        this.cursorY = 1;
        resetCursorBlink();
    }

    public void clearLine() {
        if (buffer != null && cursorY - 1 >= 0 && cursorY - 1 < buffer.length) {
            for (int x = 0; x < termWidth; x++) {
                buffer[cursorY - 1][x].setCharacter(' ', currentTextColor, currentBgColor);
            }
        }
        resetCursorBlink();
    }

    public void setCursorPos(int x, int y) {
        this.cursorX = Math.max(1, Math.min(termWidth + 1, x));
        this.cursorY = Math.max(1, Math.min(termHeight, y));
        resetCursorBlink();
    }

    public void setCursorBlink(boolean enabled) {
        this.cursorBlinkEnabled = enabled;
        if (!enabled) {
            this.cursorVisible = true;
        }
    }

    public String read() {
        try {
            return this.inputQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void showCrashScreen(String message) {
        // СТАРЫЙ КОД УДАЛЕН.
        // Этот метод больше не должен использоваться для сбоев Lua VM.
        LoraCoreClient.LOGGER.warn("TerminalRenderer.showCrashScreen() was called, but is deprecated for Lua crashes.");
    }

    public void setTextColor(int color) { this.currentTextColor = 0xFF000000 | color; }
    public void setBackgroundColor(int color) { this.currentBgColor = 0xFF000000 | color; }
    public int[] getCursorPos() { return new int[]{this.cursorX, this.cursorY}; }
    public int[] getSize() { return new int[]{this.termWidth, this.termHeight}; }

    private void scrollBuffer() {
        if (buffer == null) return;
        System.arraycopy(buffer, 1, buffer, 0, termHeight - 1);
        for (int x = 0; x < termWidth; x++) {
            if (this.buffer[termHeight - 1] != null)
                this.buffer[termHeight - 1][x] = new TerminalChar(' ', currentTextColor, currentBgColor);
        }
    }

    private void resetCursorBlink() {
        this.cursorVisible = true;
        this.tickCounter = 0;
    }
    @Override
    public void reboot() {
        // Пока не используется
    }


    private static class TerminalChar {
        char character;
        int fgColor;
        int bgColor;
        TerminalChar(char character, int fgColor, int bgColor) {
            this.character = character;
            this.fgColor = fgColor;
            this.bgColor = bgColor;
        }
        void setCharacter(char c, int fg, int bg) {
            this.character = c;
            this.fgColor = fg;
            this.bgColor = bg;
        }
    }
}