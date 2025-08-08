// Полный исправленный файл: src/main/java/com/loracore/computer/device/GpuDevice.java
package com.loracore.computer.device;

import com.loracore.api.GpuApi;
import com.loracore.computer.api.Callback;
import com.loracore.network.graphics.GpuCommand;
import java.util.UUID;

public class GpuDevice {

    private final UUID tabletUuid;

    // ИСПРАВЛЕНО: Конструктор принимает UUID
    public GpuDevice(UUID tabletUuid) {
        this.tabletUuid = tabletUuid;
    }

    @Callback(value = "fill", doc = "Fills a rectangular area with a color.")
    public void fill(int x, int y, int width, int height, int color) {
        GpuApi.sendCommand(this.tabletUuid, new GpuCommand.Fill(x, y, width, height, color));
    }

    @Callback(value = "drawText", doc = "Draws text at a specific (x, y) coordinate.")
    public void drawText(int x, int y, String text, int color) {
        GpuApi.sendCommand(this.tabletUuid, new GpuCommand.DrawText(x, y, text, color));
    }

    @Callback(value = "copy", doc = "Copies a rectangular area of the screen to another position.")
    public void copy(int x, int y, int width, int height, int toX, int toY) {
        GpuApi.sendCommand(this.tabletUuid, new GpuCommand.Copy(x, y, width, height, toX, toY));
    }
}