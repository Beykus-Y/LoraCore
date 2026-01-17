package lora.emulator.gui;

import lora.emulator.bus.KeyboardDevice;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Профессиональный обработчик ввода.
 * Не занимается детским садом с маппингом WASD,
 * а пробрасывает коды клавиш напрямую в эмулируемое "железо".
 */
public class LoraKeyHandler extends KeyAdapter {

    private final KeyboardDevice keyboard;

    public LoraKeyHandler(KeyboardDevice keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Получаем виртуальный код клавиши (например, VK_W = 87)
        int keyCode = e.getKeyCode();

        // Пробрасываем код напрямую в устройство ввода
        keyboard.pressKey(keyCode);

        // Опционально: лог для отладки, чтобы ты видел, что летит в BIOS
        // System.out.printf("[Input] Key Pressed: %d (Char: %c)%n", keyCode, (char)keyCode);
    }
}