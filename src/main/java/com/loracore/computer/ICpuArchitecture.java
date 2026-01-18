package com.loracore.computer;

public interface ICpuArchitecture {
    /**
     * Выполняет одну инструкцию.
     * @param bus Системная шина для доступа к памяти и устройствам.
     * @return Количество затраченных циклов (Cycles Spent).
     */
    int step(SystemBus bus);

    /**
     * Сброс процессора в начальное состояние.
     */
    void reset();
}