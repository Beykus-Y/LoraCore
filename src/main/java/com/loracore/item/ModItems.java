package com.loracore.item;

import com.loracore.LoraCoreMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {

    public static final Item EMERALD_SHARD = registerItem("emerald_shard",
            new EmeraldShardItem(new Item.Settings().rarity(Rarity.UNCOMMON)));

    private static Item registerItem(String name, Item item) {
        Identifier id = new Identifier(LoraCoreMod.MOD_ID, name);
        Item registeredItem = Registry.register(Registries.ITEM, id, item);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(registeredItem);
        });

        return registeredItem;
    }

    public static void registerModItems() {
        LoraCoreMod.LOGGER.info("Регистрация предметов для мода " + LoraCoreMod.MOD_ID);
    }
}
