package com.loracore.service;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries; // Добавлен импорт
import net.minecraft.village.VillagerProfession;

import java.util.Map;
import java.util.Set;

public class GiftService {

    public enum GiftTier { LOVED, LIKED, NEUTRAL, DISLIKED }

    public record GiftResult(GiftTier tier, int friendshipChange) {}

    // Предметы, которые любят жители определённых профессий
    private static final Map<VillagerProfession, Set<Item>> LOVED_GIFTS = Map.of(
            VillagerProfession.FARMER, Set.of(Items.GOLDEN_CARROT, Items.PUMPKIN_PIE),
            VillagerProfession.LIBRARIAN, Set.of(Items.WRITABLE_BOOK, Items.ENCHANTED_BOOK),
            VillagerProfession.ARMORER, Set.of(Items.DIAMOND, Items.NETHERITE_INGOT),
            VillagerProfession.FISHERMAN, Set.of(Items.TROPICAL_FISH_BUCKET),
            VillagerProfession.BUTCHER, Set.of(Items.COOKED_PORKCHOP, Items.COOKED_BEEF),
            VillagerProfession.CLERIC, Set.of(Items.GOLDEN_APPLE, Items.LAPIS_LAZULI)
    );

    // Предметы, которые не нравятся всем жителям
    private static final Set<Item> DISLIKED_GIFTS = Set.of(Items.POISONOUS_POTATO, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.PUFFERFISH);

    public static GiftResult evaluateGift(VillagerProfession profession, Item item) {
        if (DISLIKED_GIFTS.contains(item)) {
            return new GiftResult(GiftTier.DISLIKED, -15);
        }
        if (LOVED_GIFTS.getOrDefault(profession, Set.of()).contains(item)) {
            return new GiftResult(GiftTier.LOVED, 20);
        }

        // ИЗМЕНЕНИЕ: Исправлен некорректный вызов метода на правильный
        String itemId = Registries.ITEM.getId(item).toString();
        if (AiService.PROFESSION_GOAL_ITEMS.getOrDefault(profession, java.util.List.of()).contains(itemId)) {
            return new GiftResult(GiftTier.LIKED, 8);
        }

        return new GiftResult(GiftTier.NEUTRAL, 2);
    }
}