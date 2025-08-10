// Файл: src/main/java/com/loracore/computer/KernelDeviceApiImpl.java
package com.loracore.computer;

import com.loracore.computer.api.IKernelDeviceApi; // <-- Реализуем интерфейс из ядра
import com.loracore.computer.device.IDevice;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реализация IKernelDeviceApi на стороне основного мода.
 * Этот класс содержит всю логику, связанную с Minecraft.
 */
public class KernelDeviceApiImpl implements IKernelDeviceApi {

    private final PlayerEntity player;
    private final Map<String, IDevice> registeredDevices = new ConcurrentHashMap<>();
    private final Map<String, IDevice> activeDevices = new ConcurrentHashMap<>();

    public KernelDeviceApiImpl(PlayerEntity player) {
        this.player = player;
    }

    // Этот метод вызывается из мода для регистрации всех возможных устройств
    public void registerDevice(IDevice device) {
        registeredDevices.put(device.getType(), device);
    }

    @Override
    public void scanForDevices() {
        ServerWorld world = (ServerWorld) player.getWorld();
        HitResult hit = player.raycast(5.0, 0.0f, false);

        BlockPos targetPos = null;
        if (hit.getType() == HitResult.Type.BLOCK) {
            targetPos = ((BlockHitResult) hit).getBlockPos();
        }

        // Проверяем каждое зарегистрированное устройство
        for (IDevice device : registeredDevices.values()) {
            // Если игрок не смотрит на блок, rebind будет с null, и isAvailable() вернет false
            device.rebind(player, world, targetPos);

            if (device.isAvailable()) {
                activeDevices.put(device.getType(), device);
            } else {
                activeDevices.remove(device.getType());
            }
        }
    }

    @Override
    public Optional<IDevice> getDevice(String type) {
        return Optional.ofNullable(activeDevices.get(type));
    }

    @Override
    public List<IDevice> getActiveDevices() {
        return new ArrayList<>(activeDevices.values());
    }

    @Override
    public void tickDevices() {
        for (IDevice device : activeDevices.values()) {
            device.tick();
        }
    }
}