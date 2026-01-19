package com.loracore.computer;

public interface IPortDevice {
    /** Читает данные из порта */
    int read();
    /** Записывает данные в порт */
    void write(int value);
}