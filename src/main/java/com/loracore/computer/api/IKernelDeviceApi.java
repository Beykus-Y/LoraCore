
package com.loracore.computer.api;

import com.loracore.computer.device.IDevice;

import java.util.List;
import java.util.Optional;

/**
 * API, предоставляемое ядру для взаимодействия с DeviceManager'ом,
 * который находится в основном моде. Ядро не знает о мире Minecraft,
 * оно только вызывает эти методы.
 */
public interface IKernelDeviceApi {

    /**
     * Запрашивает у основного мода просканировать окружение и обновить список активных устройств.
     */
    void scanForDevices();

    /**
     * Получает активное устройство по его типу.
     * @param type Тип устройства (например, "redstone").
     * @return Optional, содержащий устройство, если оно доступно.
     */
    Optional<IDevice> getDevice(String type);

    /**
     * Получает список всех активных устройств.
     * @return Список активных устройств.
     */
    List<IDevice> getActiveDevices();

    /**
     * Вызывает tick() у всех активных устройств.
     */
    void tickDevices();
}