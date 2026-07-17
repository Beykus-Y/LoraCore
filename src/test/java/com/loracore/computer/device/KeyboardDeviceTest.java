package com.loracore.computer.device;

import com.loracore.computer.SystemBus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyboardDeviceTest {

    @Test
    public void queuedKeysRemainOrderedAndRequestKeyboardIrq() {
        SystemBus bus = new SystemBus();
        KeyboardDevice keyboard = new KeyboardDevice(bus);

        keyboard.pushKey('a');
        keyboard.pushKey('b');

        assertEquals(1, bus.checkPendingInterrupts());
        assertEquals(2, keyboard.readInt(0));
        assertEquals('a', keyboard.readInt(4));
        assertEquals('b', keyboard.readInt(4));
        assertEquals(0, keyboard.readInt(0));
        assertEquals(0, keyboard.readInt(4));
    }

    @Test
    public void keyboardQueueIsBounded() {
        KeyboardDevice keyboard = new KeyboardDevice(null);

        for (int key = 0; key < 300; key++) {
            keyboard.pushKey(key);
        }

        assertEquals(256, keyboard.readInt(0));
    }
}
