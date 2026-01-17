package lora.emulator.bus;

public class KeyboardDevice implements IMemoryMappedDevice {
    private volatile int lastKey = 0; // volatile, так как пишет UI поток, а читает CPU поток

    @Override
    public int getSize() {
        return 16; // Зарезервируем чуть-чуть места
    }

    @Override
    public byte read(int offset) {
        // Мы читаем int (4 байта), поэтому CPU вызовет read(0), read(1)...
        // Для простоты вернем клавишу только при чтении 0-го байта, а потом сбросим.
        // Но SystemBus собирает int из байтов. Это сложный момент.
        // Упростим: SystemBus вызывает readByte.
        // Сделаем так: возвращаем младший байт ключа при offset 0.

        if (offset == 0) {
            int k = lastKey;
            lastKey = 0; // Сбрасываем после чтения ("поглощаем" нажатие)
            return (byte) k;
        }
        return 0;
    }

    @Override
    public void write(int offset, byte value) {
        // Клавиатура Read-Only для процессора
    }

    // Метод для GUI, чтобы "нажать" кнопку
    public void pressKey(int keyCode) {
        this.lastKey = keyCode;
    }
}