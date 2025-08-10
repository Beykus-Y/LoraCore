// Расположение: src/main/java/com/loracore/computer/VirtualMachineState.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualMachineState extends PersistentState {

    // Карта для хранения состояний: UUID планшета -> NBT-данные его ВМ
    private final Map<UUID, NbtCompound> savedStates = new ConcurrentHashMap<>();

    // Уникальный ID для нашего хранилища в файлах мира
    private static final String ID = LoraCoreMod.MOD_ID + "_virtual_machines";

    // Фабричный метод для создания/загрузки состояния из NBT
    public static VirtualMachineState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        VirtualMachineState state = new VirtualMachineState();
        for (String key : nbt.getKeys()) {
            try {
                UUID tabletUuid = UUID.fromString(key);
                NbtCompound vmStateNbt = nbt.getCompound(key);
                state.savedStates.put(tabletUuid, vmStateNbt);
            } catch (IllegalArgumentException e) {
                LoraCoreMod.LOGGER.warn("Не удалось загрузить состояние ВМ: неверный UUID в ключе '{}'", key);
            }
        }
        return state;
    }

    // Метод для сохранения текущего состояния в NBT
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        savedStates.forEach((uuid, vmStateNbt) -> {
            nbt.put(uuid.toString(), vmStateNbt);
        });
        return nbt;
    }

    /**
     * Сохраняет состояние одной конкретной ВМ.
     * @param tabletUuid UUID планшета
     * @param vmStateNbt NBT-данные состояния его ВМ
     */
    public void saveMachineState(UUID tabletUuid, NbtCompound vmStateNbt) {
        this.savedStates.put(tabletUuid, vmStateNbt);
        this.markDirty(); // Очень важно! Помечаем, что данные изменились и их нужно сохранить.
    }

    /**
     * Загружает состояние для одной ВМ, если оно есть.
     * @param tabletUuid UUID планшета
     * @return NbtCompound с состоянием или null, если ничего не найдено.
     */
    public NbtCompound loadMachineState(UUID tabletUuid) {
        return this.savedStates.get(tabletUuid);
    }

    /**
     * Главный метод для получения доступа к нашему хранилищу из любой точки сервера.
     */
    public static VirtualMachineState get(ServerWorld world) {
        PersistentStateManager stateManager = world.getServer().getOverworld().getPersistentStateManager();

        // Получаем существующее состояние или создаем новое, если его нет
        Type<VirtualMachineState> type = new Type<>(
                VirtualMachineState::new,         // Как создать новый пустой объект
                VirtualMachineState::createFromNbt, // Как загрузить из NBT
                null                              // DataFixer, нам не нужен
        );

        return stateManager.getOrCreate(type, ID);
    }
}