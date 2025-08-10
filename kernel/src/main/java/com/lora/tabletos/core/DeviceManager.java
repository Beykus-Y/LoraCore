// Файл: kernel/src/main/java/com/lora/tabletos/core/DeviceManager.java
package com.lora.tabletos.core;

import java.util.concurrent.CompletableFuture;

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

    // Здесь можно добавить другие удобные методы для других устройств
    // public CompletableFuture<ItemStack[]> getInventoryContents() { ... }
}