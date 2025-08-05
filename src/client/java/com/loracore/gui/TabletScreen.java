// Файл: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreClient;
import com.loracore.computer.ClientVFS;
import com.loracore.computer.Terminal;
import com.loracore.computer.VirtualMachine;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TabletScreen extends Screen implements Terminal {

    // --- Константы ---
    private static final int BACKGROUND_OVERLAY_COLOR = 0xB0000000;
    private static final int TABLET_BG_COLOR = 0xFF1E1E1E;
    private static final int TABLET_BORDER_COLOR = 0xFF0A0A0A;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int FONT_HEIGHT = 9;
    private static final int FONT_WIDTH = 6;

    // --- Состояние ---
    private final VirtualMachine vm;
    private TerminalChar[][] buffer;
    private int termWidth, termHeight;
    private int cursorX = 1, cursorY = 1;
    private boolean cursorVisible = true;
    private boolean cursorBlinkEnabled = true;
    private int tickCounter = 0;
    private int tabletX, tabletY, tabletWidth, tabletHeight;
    private int currentTextColor = TEXT_COLOR;
    private int currentBgColor = TABLET_BG_COLOR;

    // --- Поля для ввода/вывода ---
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>(1);
    private String currentInputLine = "";
    private final ClientVFS vfs;

    public TabletScreen(ClientVFS vfs) {
        super(Text.literal("LoraOS"));
        this.vfs = vfs;
        // [ИСПРАВЛЕНО] Получаем активную VM из клиента, а не создаем новую.
        // Это сохраняет состояние между перерисовками экрана (ресайз).
        this.vm = LoraCoreClient.getActiveVM();
    }

    @Override
    protected void init() {
        super.init();
        calculateTabletDimensions();

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
            if(this.vm != null) {
                vm.pushEvent("term_resize");
            }
        }
    }

    @Override
    public void close() {
        // Этот метод вызывается, когда мы хотим закрыть экран (например, по нажатию ESC).
        // Фактическая очистка ресурсов произойдет в onClosed().
        super.close();
    }

    @Override
    public void removed() {
        LoraCoreClient.shutdownActiveVM();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        if (cursorBlinkEnabled) {
            tickCounter++;
            if (tickCounter >= 10) {
                tickCounter = 0;
                cursorVisible = !cursorVisible;
            }
        } else {
            cursorVisible = true;
        }

        context.fill(tabletX, tabletY, tabletX + tabletWidth, tabletY + tabletHeight, TABLET_BORDER_COLOR);

        if (buffer != null) {
            for (int y = 0; y < termHeight; y++) {
                for (int x = 0; x < termWidth; x++) {
                    TerminalChar ch = buffer[y][x];
                    int drawX = tabletX + 4 + x * FONT_WIDTH;
                    int drawY = tabletY + 4 + y * FONT_HEIGHT;
                    context.fill(drawX, drawY, drawX + FONT_WIDTH, drawY + FONT_HEIGHT, ch.bgColor);
                    context.drawTextWithShadow(textRenderer, String.valueOf(ch.character), drawX, drawY, ch.fgColor);
                }
            }
        }

        if (cursorVisible) {
            int cursorDrawX = tabletX + 4 + (cursorX - 1) * FONT_WIDTH;
            int cursorDrawY = tabletY + 4 + (cursorY - 1) * FONT_HEIGHT;
            context.fill(cursorDrawX, cursorDrawY, cursorDrawX + FONT_WIDTH, cursorDrawY + FONT_HEIGHT, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifiers == GLFW.GLFW_MOD_CONTROL && keyCode == GLFW.GLFW_KEY_C) {
            if (vm != null) {
                vm.pushEvent("signal", "interrupt");
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            try {
                this.inputQueue.put(this.currentInputLine);
                this.print("\n");
                this.currentInputLine = "";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!this.currentInputLine.isEmpty()) {
                this.currentInputLine = this.currentInputLine.substring(0, this.currentInputLine.length() - 1);
                if (this.cursorX > 1) {
                    this.setCursorPos(this.cursorX - 1, this.cursorY);
                    this.print(" ");
                    this.setCursorPos(this.cursorX - 1, this.cursorY);
                }
            }
            return true;
        }
        if (vm != null) {
            vm.pushEvent("key", keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        this.currentInputLine += chr;
        this.print(String.valueOf(chr));
        return true;
    }

    @Override
    public void print(String text) {
        for (char ch : text.toCharArray()) {
            if (ch == '\n') {
                cursorX = 1;
                cursorY++;
            } else {
                if (cursorX > termWidth) {
                    cursorX = 1;
                    cursorY++;
                }
                if (cursorY > termHeight) {
                    scrollBuffer();
                    cursorY = termHeight;
                }
                if (buffer != null && cursorY - 1 >= 0 && cursorY - 1 < buffer.length && cursorX - 1 >= 0 && cursorX - 1 < buffer[0].length) {
                    buffer[cursorY - 1][cursorX - 1].setCharacter(ch, currentTextColor, currentBgColor);
                    cursorX++;
                }
            }
        }
        resetCursorBlink();
    }

    @Override
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

    @Override
    public void clearLine() {
        if (buffer != null && cursorY - 1 >= 0 && cursorY - 1 < buffer.length) {
            for (int x = 0; x < termWidth; x++) {
                buffer[cursorY - 1][x].setCharacter(' ', currentTextColor, currentBgColor);
            }
        }
        resetCursorBlink();
    }

    @Override
    public void setCursorPos(int x, int y) {
        this.cursorX = Math.max(1, Math.min(termWidth + 1, x));
        this.cursorY = Math.max(1, Math.min(termHeight, y));
        resetCursorBlink();
    }

    @Override
    public String read() {
        try {
            return this.inputQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void reboot() {
        if (this.client != null) {
            this.client.execute(() -> {
                LoraCoreClient.shutdownActiveVM();
                this.client.setScreen(null); // Просто закрываем экран, при следующем использовании предмета создастся новая сессия.
            });
        }
    }

    @Override
    public void setTextColor(int color) { this.currentTextColor = 0xFF000000 | color; }

    @Override
    public void setBackgroundColor(int color) { this.currentBgColor = 0xFF000000 | color; }

    @Override
    public int[] getCursorPos() { return new int[]{this.cursorX, this.cursorY}; }

    @Override
    public int[] getSize() { return new int[]{this.termWidth, this.termHeight}; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, BACKGROUND_OVERLAY_COLOR);
    }

    @Override
    public boolean shouldPause() { return false; }

    private void calculateTabletDimensions() {
        this.tabletHeight = (int) (this.height * 0.9);
        this.tabletWidth = (int) (this.tabletHeight * (16.0 / 10.0));
        if (this.tabletWidth > this.width * 0.95) {
            this.tabletWidth = (int) (this.width * 0.95);
            this.tabletHeight = (int) (this.tabletWidth * (10.0 / 16.0));
        }
        this.tabletX = (this.width - this.tabletWidth) / 2;
        this.tabletY = (this.height - this.tabletHeight) / 2;
    }

    private void scrollBuffer() {
        if (buffer == null) return;
        for (int y = 0; y < termHeight - 1; y++) {
            if (this.buffer[y] != null && this.buffer[y+1] != null)
                System.arraycopy(this.buffer[y + 1], 0, this.buffer[y], 0, termWidth);
        }
        for (int x = 0; x < termWidth; x++) {
            if (this.buffer[termHeight-1] != null)
                this.buffer[termHeight - 1][x] = new TerminalChar(' ', currentTextColor, currentBgColor);
        }
    }

    private void resetCursorBlink() {
        this.cursorVisible = true;
        this.tickCounter = 0;
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
    @Override
    public void setCursorBlink(boolean enabled) {
        this.cursorBlinkEnabled = enabled;
        // Если мигание отключается, курсор должен стать видимым немедленно
        if (!enabled) {
            this.cursorVisible = true;
        }
    }
    @Override
    public void showCrashScreen(String message) {
        // Убеждаемся, что мы находимся в потоке клиента
        if (this.client != null) {
            // Просто говорим клиенту установить новый экран - наш CrashScreen.
            // Передаем ему текущий экран (this) как родительский, на который можно вернуться.
            this.client.setScreen(new CrashScreen(message, this));
        }
    }
}