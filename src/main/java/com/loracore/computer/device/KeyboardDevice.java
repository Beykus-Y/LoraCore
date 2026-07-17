package com.loracore.computer.device;

import com.loracore.computer.IMemoryMappedDevice;
import com.loracore.computer.SystemBus;

import java.util.concurrent.ConcurrentLinkedQueue;

public class KeyboardDevice implements IMemoryMappedDevice {
    // Смещения регистров (должны совпадать с consts.lc)
    // 0x00: STATUS (Сколько клавиш в буфере)
    // 0x04: DATA (Прочитать клавишу и удалить из очереди)

    private final ConcurrentLinkedQueue<Integer> keyBuffer = new ConcurrentLinkedQueue<>();
    private SystemBus bus;

    public KeyboardDevice(SystemBus bus) {
        this.bus = bus;
    }
    @Override
    public int getSize() {
        return 16; // Небольшое окно
    }

    @Override
    public byte read(int offset) {
        // Поддержка побайтового чтения, если вдруг понадобится (обычно читают int)
        if (offset == 0) return (byte) keyBuffer.size();
        return 0;
    }

    @Override
    public int readInt(int offset) {
        if (offset == 0) { // KEYB_REG_CNT
            return keyBuffer.size();
        }
        if (offset == 4) { // KEYB_REG_VAL
            Integer key = keyBuffer.poll();
            return key != null ? key : 0;
        }
        return 0;
    }

    @Override
    public void write(int offset, byte value) {
        // Read-only для CPU
    }

    @Override
    public void writeInt(int offset, int value) {
        // Можно добавить команду сброса буфера, если нужно
    }

    // Метод для добавления клавиш извне (из пакетов Minecraft)
    public void pushKey(int keyCode) {
        if (keyBuffer.size() < 256) {
            keyBuffer.add(keyCode);
            // Дергаем IRQ 1
            if (bus != null) bus.requestInterrupt(1);
        }
    }
}