package com.loracore.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class EmeraldShardItem extends Item {
    public EmeraldShardItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true; // предмет всегда будет мерцать, как зачарованный
    }
}
