// --- Новый файл: src/main/java/com/loracore/computer/device/DiskManagerDevice.java ---

package com.loracore.computer.device;

import com.google.gson.Gson;
import com.loracore.computer.ImageVfsManager;
import com.loracore.computer.api.Callback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

/**
 * Серверное устройство для управления дисковыми образами (форматирование, информация).
 * Это "виртуальное" устройство, оно доступно всегда, когда планшет включен.
 */
public class DiskManagerDevice implements IDevice {

    private PlayerEntity player;
    private static final Gson GSON = new Gson();

    @Override
    public String getType() {
        return "disk_manager";
    }

    @Override
    public void rebind(PlayerEntity player, ServerWorld world, BlockPos targetPos) {
        // Этому устройству не нужен мир или цель, только игрок
        this.player = player;
    }

    @Override
    public boolean isAvailable() {
        // Устройство всегда доступно, если есть игрок
        return this.player != null;
    }

    @Callback(doc = "Formats the disk image associated with the given UUID, deleting all its contents.")
    public boolean format(String fsUuidStr) {
        if (fsUuidStr == null || fsUuidStr.isEmpty()) {
            throw new IllegalArgumentException("File system UUID cannot be null or empty.");
        }
        UUID fsUuid = UUID.fromString(fsUuidStr);
        return ImageVfsManager.getInstance().formatImage(fsUuid);
    }

    @Callback(doc = "Returns information about the specified disk.")
    public String getDiskInfo(String fsUuidStr) {
        if (fsUuidStr == null || fsUuidStr.isEmpty()) {
            throw new IllegalArgumentException("File system UUID cannot be null or empty.");
        }
        UUID fsUuid = UUID.fromString(fsUuidStr);
        return ImageVfsManager.getInstance().getDiskInfoAsJson(fsUuid);
    }
}