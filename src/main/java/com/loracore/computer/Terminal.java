// [ИЗМЕНЕНО]
// Файл: src/main/java/com/loracore/computer/Terminal.java
package com.loracore.computer;

public interface Terminal {
    void print(String text);
    void clear();
    void setCursorPos(int x, int y);
    void clearLine();
    String read();
    void setTextColor(int color);
    void setBackgroundColor(int color);
    int[] getCursorPos();
    int[] getSize();
    void reboot();
    void showCrashScreen(String message);

    // [НОВОЕ] Добавлен метод для управления миганием курсора
    void setCursorBlink(boolean enabled);
}