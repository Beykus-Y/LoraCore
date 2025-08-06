// Новый файл: src/main/java/com/loracore/computer/RebootSignalException.java
package com.loracore.computer;

/**
 * Это не ошибка, а специальный сигнал в виде исключения.
 * Он используется, чтобы немедленно прервать выполнение Lua-потока
 * при вызове os.reboot() и позволить потоку чисто завершиться.
 */
public class RebootSignalException extends RuntimeException {
    public RebootSignalException() {
        super("System is rebooting.");
    }
}