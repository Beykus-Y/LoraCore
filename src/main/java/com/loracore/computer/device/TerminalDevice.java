// [ИЗМЕНЕНО]
// Файл: src/main/java/com/loracore/computer/device/TerminalDevice.java
package com.loracore.computer.device;

import com.loracore.api.ClientApi;
import com.loracore.computer.Terminal;
import com.loracore.computer.api.Callback;

/**
 * Предоставляет API для взаимодействия с терминалом.
 * Методы этого класса будут автоматически доступны в Lua.
 */
public class TerminalDevice {
    private final Terminal terminal;

    public TerminalDevice(Terminal terminal) {
        this.terminal = terminal;
    }

    @Callback(value = "write", doc = "Writes text to the current cursor position.")
    public void write(String text) {
        // Мы оборачиваем все вызовы GUI в executeOnRenderThread, чтобы избежать
        // проблем с многопоточностью, так как Lua VM работает в отдельном потоке.
        ClientApi.executeOnRenderThread(() -> terminal.print(text));
    }
    @Callback(value = "print", doc = "Writes text to the current cursor position and adds a newline.")
    public void print(String text) {
        // Реализуем print как write + newline
        ClientApi.executeOnRenderThread(() -> terminal.print(text + "\n"));
    }

    @Callback(doc = "Clears the entire terminal screen.")
    public void clear() {
        ClientApi.executeOnRenderThread(terminal::clear);
    }

    @Callback(doc = "Clears the line the cursor is currently on.")
    public void clearLine() {
        ClientApi.executeOnRenderThread(terminal::clearLine);
    }

    @Callback(doc = "Sets the cursor's position. Top-left is (1, 1).")
    public void setCursorPos(int x, int y) {
        ClientApi.executeOnRenderThread(() -> terminal.setCursorPos(x, y));
    }

    @Callback(doc = "Enables or disables the cursor's blinking.")
    public void setCursorBlink(boolean enabled) {
        ClientApi.executeOnRenderThread(() -> terminal.setCursorBlink(enabled));
    }

    @Callback(doc = "Sets the foreground color for subsequent text.")
    public void setTextColor(int color) {
        ClientApi.executeOnRenderThread(() -> terminal.setTextColor(color));
    }

    @Callback(doc = "Sets the background color for subsequent text.")
    public void setBackgroundColor(int color) {
        ClientApi.executeOnRenderThread(() -> terminal.setBackgroundColor(color));
    }

    @Callback(value = "getCursorPos", doc = "Returns the current cursor position as (x, y).")
    public int[] getCursorPosition() { // Изменено имя для избежания конфликта с getCursorPos() в Terminal
        return terminal.getCursorPos();
    }

    @Callback(value = "getSize", doc = "Returns the terminal's size in characters as (width, height).")
    public int[] getTerminalSize() { // Изменено имя для избежания конфликта
        return terminal.getSize();
    }
    @Callback(doc = "Reads a line of input from the user, waiting for Enter.")
    public String read() {
        // Вызов terminal.read() является блокирующим и потокобезопасным,
        // поэтому его можно вызывать напрямую из потока Lua VM.
        return terminal.read();
    }
}