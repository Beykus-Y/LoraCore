// Файл: kernel/src/main/java/com/lora/tabletos/core/DeviceManager.java
package com.lora.tabletos.core;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil.GSON;

/**
 * ✅ АСИНХРОННАЯ ВЕРСИЯ
 * DeviceManager ядра. Предоставляет удобный API для асинхронного вызова
 * серверных устройств из Java-приложений.
 */
public class DeviceManager {

    private final IApplicationApi appApi;

    public DeviceManager(IApplicationApi appApi) {
        this.appApi = appApi;
    }

    /**
     * Асинхронно вызывает метод на устройстве Redstone.
     * @param side Направление (например, "north").
     * @return Future, который завершится с уровнем сигнала (0-15).
     */
    public CompletableFuture<Integer> getRedstonePower(String side) {
        return appApi.invokeDevice("redstone", "getPower", side)
                .thenApply(result -> {
                    // Результат приходит как Object[], даже если он один.
                    // Также он может быть Double из-за JSON-сериализации.
                    if (result != null && result.length > 0 && result[0] instanceof Double) {
                        return ((Double) result[0]).intValue();
                    }
                    return 0; // Возвращаем 0 в случае ошибки
                });
    }

    /**
     * Асинхронно форматирует диск.
     * @param fsUuid Уникальный идентификатор файловой системы диска.
     * @return Future, который завершится с true в случае успеха.
     */
    public CompletableFuture<Boolean> formatDisk(String fsUuid) {
        return appApi.invokeDevice("disk_manager", "format", fsUuid)
                .thenApply(result -> result != null && result.length > 0 && (Boolean)result[0]);
    }

    /**
     * Асинхронно запрашивает информацию о диске.
     * @param fsUuid Уникальный идентификатор файловой системы диска.
     * @return Future, который завершится с картой, содержащей информацию о диске.
     */
    public CompletableFuture<Map<String, Object>> getDiskInfo(String fsUuid) {
        return appApi.invokeDevice("disk_manager", "getDiskInfo", fsUuid)
                .thenApply(result -> {
                    if (result != null && result.length > 0 && result[0] instanceof String json) {
                        Type type = new TypeToken<Map<String, Object>>(){}.getType();
                        return GSON.fromJson(json, type);
                    }
                    // Возвращаем пустую карту в случае ошибки
                    return Map.of();
                });
    }
    /**
     * Асинхронно запрашивает список установленных накопителей.
     * @return Future, который завершится со списком карт, где каждая карта представляет диск.
     */
    public CompletableFuture<List<Map<String, String>>> listDisks() {
        return appApi.invokeDevice("motherboard", "getStorageDevices")
                .thenApply(result -> {
                    if (result != null && result.length > 0 && result[0] instanceof String json) {
                        Type type = new TypeToken<List<Map<String, String>>>(){}.getType();
                        return GSON.fromJson(json, type);
                    }
                    return List.of(); // Возвращаем пустой список в случае ошибки
                });
    }

    // Здесь можно добавить другие удобные методы для других устройств
    // public CompletableFuture<ItemStack[]> getInventoryContents() { ... }
}