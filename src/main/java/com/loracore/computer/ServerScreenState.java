// Новый файл: src/main/java/com/loracore/computer/ServerScreenState.java
package com.loracore.computer;

import com.loracore.service.ServerFont;

/**
 * Простой класс-контейнер для хранения состояния экрана одного планшета на сервере.
 * Не является компонентом, управляется через TabletScreenManager.
 */
public class ServerScreenState {

    private byte[] pixelBuffer;
    private boolean isDirty = false;
    private int width = 0;
    private int height = 0;

    public ServerScreenState() {
        this.initialize(ServerFont.SCREEN_WIDTH, ServerFont.SCREEN_HEIGHT);
    }

    private void initialize(int width, int height) {
        if (this.pixelBuffer == null || this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            this.pixelBuffer = new byte[width * height * 4];
            // Заливаем черным цветом по умолчанию (R=0, G=0, B=0, A=255)
            for (int i = 0; i < this.pixelBuffer.length; i += 4) {
                this.pixelBuffer[i] = 0;
                this.pixelBuffer[i + 1] = 0;
                this.pixelBuffer[i + 2] = 0;
                this.pixelBuffer[i + 3] = (byte) 255;
            }
            this.isDirty = true;
        }
    }

    public byte[] getPixelBuffer() {
        return this.pixelBuffer;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void clearDirtyFlag() {
        this.isDirty = false;
    }

    public void clearBuffer() {
        java.util.Arrays.fill(this.pixelBuffer, (byte)0);
        for (int i = 3; i < this.pixelBuffer.length; i += 4) {
            this.pixelBuffer[i] = (byte)255; // Alpha
        }
        this.isDirty = true;
    }
}