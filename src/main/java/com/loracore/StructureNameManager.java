package com.loracore;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;
import com.loracore.service.AiService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StructureNameManager extends PersistentState {
    private static final String ID = "loracore_structure_names";

    public static class StructureData {
        public String name;
        public String description;
        public AtomicBoolean isGenerating = new AtomicBoolean(false);

        public StructureData(String name, String description) { this.name = name; this.description = description; }
        public StructureData(NbtCompound nbt) { this.name = nbt.getString("name"); this.description = nbt.getString("description"); }
        public NbtCompound writeNbt() { NbtCompound nbt = new NbtCompound(); nbt.putString("name", name); nbt.putString("description", description); return nbt; }
    }

    public static class StructureCheckResult {
        private final Optional<StructureData> data;
        private final Optional<String> posKey;

        public StructureCheckResult(Optional<StructureData> data, Optional<String> posKey) {
            this.data = data;
            this.posKey = posKey;
        }

        public Optional<StructureData> getData() { return data; }
        public Optional<String> getPosKey() { return posKey; }

        public static StructureCheckResult empty() {
            return new StructureCheckResult(Optional.empty(), Optional.empty());
        }
    }

    private final Map<String, StructureData> structureDataMap = new ConcurrentHashMap<>();

    public StructureNameManager() {}

    public static StructureNameManager createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        StructureNameManager manager = new StructureNameManager();
        NbtList list = nbt.getList("structures", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : list) {
            NbtCompound compound = (NbtCompound) element;
            String posKey = compound.getString("pos");
            StructureData data = new StructureData(compound);
            manager.structureDataMap.put(posKey, data);
        }
        LoraCoreMod.LOGGER.info("Загружено {} записей о структурах.", manager.structureDataMap.size());
        return manager;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Map.Entry<String, StructureData> entry : structureDataMap.entrySet()) {
            NbtCompound compound = entry.getValue().writeNbt();
            compound.putString("pos", entry.getKey());
            list.add(compound);
        }
        nbt.put("structures", list);
        LoraCoreMod.LOGGER.info("Сохранено {} записей о структурах.", structureDataMap.size());
        return nbt;
    }

    public static StructureNameManager get(ServerWorld world) {
        PersistentStateManager stateManager = world.getPersistentStateManager();
        Type<StructureNameManager> type = new Type<>(
                StructureNameManager::new,
                StructureNameManager::createFromNbt, // Заменено на method reference
                null
        );
        return stateManager.getOrCreate(type, ID);
    }

    // =========================================================================
    // ИСПРАВЛЕННАЯ ЛОГИКА ПОИСКА
    // =========================================================================
    public StructureCheckResult getOrCreateStructureDataAt(ServerWorld world, BlockPos playerPos) {
        List<TagKey<Structure>> structureTags = List.of(
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "village")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "pillager_outpost")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "mineshaft")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "desert_pyramid")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "jungle_temple")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "ocean_monument")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "stronghold")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "mansion")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "ruined_portal")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "shipwreck")),
                TagKey.of(RegistryKeys.STRUCTURE, new Identifier("minecraft", "swamp_hut"))
        );

        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        for (TagKey<Structure> tag : structureTags) {
            Optional<RegistryEntryList.Named<Structure>> entryListOptional = structureRegistry.getEntryList(tag);
            if (entryListOptional.isEmpty()) continue;

            // НОВЫЙ ПОДХОД: Перебираем каждую структуру внутри тега
            for (RegistryEntry<Structure> structureEntry : entryListOptional.get()) {
                // Получаем ключ для каждой конкретной структуры (например, minecraft:village_plains)
                Optional<RegistryKey<Structure>> keyOptional = structureEntry.getKey();
                if (keyOptional.isEmpty()) continue;

                // Используем правильный метод API с правильными аргументами
                StructureStart structureStart = world.getStructureAccessor().getStructureAt(playerPos, structureEntry.value());

                if (structureStart != null && structureStart.hasChildren()) {
                    BlockPos structureCenter = structureStart.getBoundingBox().getCenter();
                    String posKey = structureCenter.getX() + "," + structureCenter.getY() + "," + structureCenter.getZ();

                    StructureData data = structureDataMap.computeIfAbsent(posKey, k -> {
                        StructureData newData = new StructureData("Неизвестная структура...", "Получаем описание...");
                        newData.isGenerating.set(true);

                        RegistryEntry<Biome> biomeEntry = world.getBiome(playerPos);
                        String biomeId = biomeEntry.getKey().map(RegistryKey::getValue).orElse(new Identifier("minecraft", "unknown_biome")).toString();

                        LoraCoreMod.LOGGER.info("Найдена новая структура (тег '{}') в биоме '{}' по позиции {}. Запускаем генерацию имени AI.", tag.id(), biomeId, posKey);

                        AiService.generateStructureInfo(tag.id().toString(), biomeId)
                                .whenCompleteAsync((generatedInfo, error) -> {
                                    MinecraftServer server = world.getServer();
                                    // Проверка server != null здесь избыточна, так как мы находимся на сервере
                                    server.execute(() -> {
                                        if (error != null) {
                                            LoraCoreMod.LOGGER.error("Ошибка при генерации имени AI для структуры в {}: {}", posKey, error.getMessage());
                                            structureDataMap.put(posKey, new StructureData("Ошибка генерации", "Не удалось получить описание."));
                                        } else {
                                            LoraCoreMod.LOGGER.info("AI сгенерировал имя '{}' и описание '{}' для структуры в {}.", generatedInfo.name(), generatedInfo.description(), posKey);
                                            structureDataMap.put(posKey, new StructureData(generatedInfo.name(), generatedInfo.description()));
                                        }
                                        StructureData currentData = structureDataMap.get(posKey);
                                        if (currentData != null) {
                                            currentData.isGenerating.set(false);
                                        }
                                        markDirty();
                                    });
                                }, world.getServer());
                        return newData;
                    });
                    // Как только нашли структуру, выходим из обоих циклов и возвращаем результат
                    return new StructureCheckResult(Optional.of(data), Optional.of(posKey));
                }
            }
        }
        // Если после всех проверок ничего не найдено
        return StructureCheckResult.empty();
    }
}