package com.loracore.computer;

public interface IInterruptHandler {
    /**
     * Обрабатывает программное прерывание.
     * @param code код прерывания (imm16 из инструкции)
     * @param regs ссылка на массив регистров CPU для чтения/записи параметров
     */
    void handle(int code, int[] regs);
}