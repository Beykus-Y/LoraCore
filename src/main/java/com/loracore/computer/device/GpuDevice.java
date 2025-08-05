// Файл: src/main/java/com/loracore/computer/device/GpuDevice.java
package com.loracore.computer.device;

import com.loracore.api.ClientApi;
import com.loracore.computer.Terminal;
import com.loracore.computer.api.Callback;

/**
 * Предоставляет низкоуровневый API для рендеринга на экране.
 */
public class GpuDevice {
    private final Terminal terminal;

    public GpuDevice(Terminal terminal) {
        this.terminal = terminal;
    }

    @Callback(value = "getResolution", doc = "Returns the screen resolution in characters as (width, height).")
    public int[] getResolution() {
        return terminal.getSize();
    }

    @Callback(value = "set", doc = "Draws text at a specific (x, y) coordinate.")
    public void set(int x, int y, String text) {
        ClientApi.executeOnRenderThread(() -> {
            terminal.setCursorPos(x, y);
            terminal.print(text);
        });
    }

    @Callback(value = "fill", doc = "Fills a rectangular area with a character.")
    public void fill(int x, int y, int width, int height, String character) {
        if (character == null || character.isEmpty()) return;
        final String repeatedChar = character.substring(0, 1).repeat(width);

        ClientApi.executeOnRenderThread(() -> {
            for (int j = 0; j < height; j++) {
                terminal.setCursorPos(x, y + j);
                terminal.print(repeatedChar);
            }
        });
    }

    @Callback(value = "setTextColor", doc = "Sets the foreground color.")
    public void setForegroundColor(int color) { // Переименовано для ясности
        ClientApi.executeOnRenderThread(() -> terminal.setTextColor(color));
    }

    @Callback(value = "setBackgroundColor", doc = "Sets the background color.")
    public void setBackgroundColor(int color) {
        ClientApi.executeOnRenderThread(() -> terminal.setBackgroundColor(color));
    }
}