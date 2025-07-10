package com.loracore.component;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity; // ИМПОРТ
import net.minecraft.util.Identifier;
import com.loracore.AiMod;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

public class ModComponents implements EntityComponentInitializer {

    public static final ComponentKey<VillagerDataComponent> VILLAGER_DATA =
            ComponentRegistry.getOrCreate(new Identifier(AiMod.MOD_ID, "villager_data"), VillagerDataComponent.class);

    // НОВЫЙ КЛЮЧ ДЛЯ КОМПОНЕНТА ИГРОКА
    public static final ComponentKey<PlayerDialogueComponent> PLAYER_DIALOGUE =
            ComponentRegistry.getOrCreate(new Identifier(AiMod.MOD_ID, "player_dialogue"), PlayerDialogueComponent.class);
    public static final ComponentKey<PlayerQuestComponent> PLAYER_QUEST =
            ComponentRegistry.getOrCreate(new Identifier(AiMod.MOD_ID, "player_quest"), PlayerQuestComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(VillagerEntity.class, VILLAGER_DATA, villager -> new VillagerDataComponentImpl());

        // РЕГИСТРАЦИЯ НОВОГО КОМПОНЕНТА ДЛЯ ИГРОКА
        registry.registerFor(PlayerEntity.class, PLAYER_DIALOGUE, player -> new PlayerDialogueComponentImpl());

        registry.registerFor(PlayerEntity.class, PLAYER_QUEST, player -> new PlayerQuestComponentImpl());
    }
}