// Полный исправленный файл: src/main/java/com/loracore/component/ModComponents.java
package com.loracore.component;

import com.loracore.LoraCoreMod;
import com.loracore.component.data.*;
import net.minecraft.component.DataComponentType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

import java.util.UUID;
import java.util.function.Function;

// Класс теперь реализует ТОЛЬКО EntityComponentInitializer.
// Все, что касалось предметов (ItemComponentInitializer), удалено.
public class ModComponents implements EntityComponentInitializer {

    // --- Cardinal Components (для хранения данных на СУЩНОСТЯХ) ---
    // Этот блок остается без изменений.

    public static final ComponentKey<VillagerDataComponent> VILLAGER_DATA =
            ComponentRegistry.getOrCreate(new Identifier(LoraCoreMod.MOD_ID, "villager_data"), VillagerDataComponent.class);

    public static final ComponentKey<PlayerDialogueComponent> PLAYER_DIALOGUE =
            ComponentRegistry.getOrCreate(new Identifier(LoraCoreMod.MOD_ID, "player_dialogue"), PlayerDialogueComponent.class);

    public static final ComponentKey<PlayerQuestComponent> PLAYER_QUEST =
            ComponentRegistry.getOrCreate(new Identifier(LoraCoreMod.MOD_ID, "player_quest"), PlayerQuestComponent.class);

    public static final ComponentKey<PlayerAskHistoryComponent> PLAYER_ASK_HISTORY =
            ComponentRegistry.getOrCreate(new Identifier(LoraCoreMod.MOD_ID, "player_ask_history"), PlayerAskHistoryComponent.class);

    // --- Vanilla Data Components (для хранения данных на ПРЕДМЕТАХ, ItemStack'ах) ---
    // Этот блок использует стандартный механизм Minecraft 1.20.5+

    public static final DataComponentType<CpuData> CPU_DATA = register("cpu_data", builder -> builder.codec(CpuData.CODEC).build());
    public static final DataComponentType<GpuData> GPU_DATA = register("gpu_data", builder -> builder.codec(GpuData.CODEC).build());
    public static final DataComponentType<RamData> RAM_DATA = register("ram_data", builder -> builder.codec(RamData.CODEC).build());
    public static final DataComponentType<FirmwareData> FIRMWARE_DATA = register("firmware_data", builder -> builder.codec(FirmwareData.CODEC).build());
    public static final DataComponentType<StorageData> STORAGE_DATA = register("storage_data", builder -> builder.codec(StorageData.CODEC).build());
    public static final DataComponentType<MotherboardData> MOTHERBOARD_DATA = register("motherboard_data", builder -> builder.codec(MotherboardData.CODEC).packetCodec(MotherboardData.PACKET_CODEC).build());
    public static final DataComponentType<FileSystemsData> FILE_SYSTEMS_DATA = register("file_systems_data", builder -> builder
            .codec(FileSystemsData.CODEC)
            .packetCodec(FileSystemsData.PACKET_CODEC)
            .build());

    // НОВЫЙ КОМПОНЕНТ: Хранит уникальный и постоянный UUID для каждого экземпляра планшета.
    // Это замена старому механизму CCA для предметов.
    public static final DataComponentType<UUID> TABLET_UUID = register("tablet_uuid", builder -> builder
            .codec(Uuids.CODEC)
            .packetCodec(Uuids.PACKET_CODEC)
            .build());

    /**
     * Вспомогательный метод для регистрации Data Component в реестре Minecraft.
     */
    private static <T> DataComponentType<T> register(String id, Function<DataComponentType.Builder<T>, DataComponentType<T>> factory) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, new Identifier(LoraCoreMod.MOD_ID, id), factory.apply(DataComponentType.builder()));
    }

    /**
     * Регистрация компонентов Cardinal Components на сущностях.
     * Этот метод вызывается благодаря интерфейсу EntityComponentInitializer.
     */
    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(VillagerEntity.class, VILLAGER_DATA, villager -> new VillagerDataComponentImpl());
        registry.registerFor(PlayerEntity.class, PLAYER_DIALOGUE, player -> new PlayerDialogueComponentImpl());
        registry.registerFor(PlayerEntity.class, PLAYER_QUEST, player -> new PlayerQuestComponentImpl());
        registry.registerFor(PlayerEntity.class, PLAYER_ASK_HISTORY, player -> new PlayerAskHistoryComponentImpl());
    }

    // Метод registerItemComponentFactories был полностью удален, так как он был частью
    // удаленного модуля cardinal-components-item.
}