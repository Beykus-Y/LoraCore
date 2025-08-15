// Файл: src/main/java/com/loracore/computer/ImageVfsManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управляет экземплярами ImageVfs для каждого диска.
 */
public class ImageVfsManager {
    private static final ImageVfsManager INSTANCE = new ImageVfsManager();
    private final Map<UUID, ImageVfs> vfsInstances = new ConcurrentHashMap<>();
    private Path worldSavePath;

    private ImageVfsManager() {}

    public static ImageVfsManager getInstance() {
        return INSTANCE;
    }

    public void initialize(MinecraftServer server) {
        this.worldSavePath = server.getSavePath(WorldSavePath.ROOT);
    }

    public ImageVfs getFor(UUID fsUuid, int capacityKb) {
        if (worldSavePath == null) {
            throw new IllegalStateException("ImageVfsManager has not been initialized!");
        }
        return vfsInstances.computeIfAbsent(fsUuid,
                uuid -> new ImageVfs(worldSavePath, uuid, capacityKb));
    }

    /**
     * Форматирует (удаляет) файл-образ диска.
     * @param fsUuid UUID файловой системы для форматирования.
     * @return true в случае успеха.
     */
    public boolean formatImage(UUID fsUuid) {
        if (worldSavePath == null) return false;

        Path imagePath = worldSavePath.resolve("loracore_vfs").resolve(fsUuid + ".img");
        try {
            vfsInstances.remove(fsUuid); // Удаляем из кэша
            return Files.deleteIfExists(imagePath);
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Failed to format disk image {}", fsUuid, e);
            return false;
        }
    }

    /**
     * Собирает и возвращает информацию о диске в виде JSON-строки.
     * @param fsUuid UUID файловой системы.
     * @return Строка JSON с информацией или пустой объект.
     */
    public String getDiskInfoAsJson(UUID fsUuid) {
        ImageVfs vfs = vfsInstances.get(fsUuid);
        if (vfs == null) {
            // Если VFS не в памяти, попробуем загрузить, чтобы получить инфо
            // Емкость здесь не важна, так как мы только читаем метаданные
            vfs = getFor(fsUuid, 0);
        }

        if (vfs != null) {
            return vfs.getMetadataAsJson();
        }
        return "{}";
    }
}