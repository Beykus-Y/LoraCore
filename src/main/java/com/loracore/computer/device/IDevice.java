// Файл: src/main/java/com/loracore/computer/device/IDevice.java
package com.loracore.computer.device;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Базовый интерфейс для всех виртуальных устройств, подключаемых к LoraOS.
 * Устройство представляет собой программную абстракцию для взаимодействия с миром Minecraft.
 * Жизненный цикл устройства управляется DeviceManager'ом.
 */
public interface IDevice {

    /**
     * Возвращает уникальный тип устройства в виде строки (например, "redstone", "inventory").
     * Это имя используется приложениями для запроса доступа к устройству.
     * @return Тип устройства.
     */
    String getType();

    /**
     * Привязывает устройство к конкретному контексту в мире (игрок, мир, целевая позиция).
     * Этот метод вызывается DeviceManager'ом перед проверкой доступности.
     * @param player Игрок, использующий планшет.
     * @param world Мир, в котором находится игрок.
     * @param targetPos Позиция блока, с которым потенциально будет взаимодействовать устройство.
     */
    void rebind(PlayerEntity player, ServerWorld world, BlockPos targetPos);

    /**
     * Проверяет, доступно ли устройство в текущем контексте после привязки.
     * Например, RedstoneDevice вернет true, если целевой блок может взаимодействовать с редстоуном.
     * @return true, если устройство готово к работе.
     */
    boolean isAvailable();

    /**
     * Вызывается каждый тик, пока устройство активно.
     * Полезно для устройств, которым нужно отслеживать изменения состояния (например, уровень энергии).
     * Можно оставить пустым для простых устройств.
     */
    default void tick() {
        // По умолчанию ничего не делает
    }
}