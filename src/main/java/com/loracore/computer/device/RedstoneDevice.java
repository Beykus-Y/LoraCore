// Файл: src/main/java/com/loracore/computer/device/RedstoneDevice.java
package com.loracore.computer.device;

import com.loracore.computer.api.Callback; // Ваша аннотация
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Locale;

public class RedstoneDevice implements IDevice {

    private ServerWorld world;
    private BlockPos targetPos;

    @Override
    public String getType() {
        return "redstone";
    }

    @Override
    public void rebind(net.minecraft.entity.player.PlayerEntity player, ServerWorld world, BlockPos targetPos) {
        this.world = world;
        this.targetPos = targetPos;
    }

    @Override
    public boolean isAvailable() {
        // Устройство доступно, если оно привязано к миру и позиции,
        // и если целевой блок является источником редстоун-сигнала для любого направления.
        // Метод isEmittingRedstonePower возвращает boolean, поэтому сравнение с 0 не нужно.
        if (this.world == null || this.targetPos == null) {
            return false;
        }
        // Проверяем, излучает ли блок сигнал в любую сторону. Это более универсально.
        for (Direction side : Direction.values()) {
            if (this.world.isEmittingRedstonePower(this.targetPos, side)) {
                return true;
            }
        }
        return false;
    }

    @Callback(doc = "Returns redstone power level (0-15) from a specific side of the block.")
    public int getPower(String side) {
        if (!isAvailable()) {
            throw new IllegalStateException("Redstone device is not available.");
        }
        try {
            Direction direction = Direction.valueOf(side.toUpperCase(Locale.ROOT));
            // Возвращает силу сигнала, которую блок ПОЛУЧАЕТ с этой стороны
            return world.getEmittedRedstonePower(targetPos, direction);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid side: " + side + ". Use NORTH, SOUTH, EAST, WEST, UP, DOWN.");
        }
    }

    // ПРИМЕЧАНИЕ: setPower() для простоты не реализован,
    // так как он требует создания своего блока-контроллера.
    // getPower() может работать с любыми существующими блоками.
}