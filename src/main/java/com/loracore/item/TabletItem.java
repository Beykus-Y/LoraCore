// Файл: src/main/java/com/loracore/item/TabletItem.java
package com.loracore.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class TabletItem extends Item {

    public TabletItem(Settings settings) {
        super(settings);
    }

    // Метод use теперь может быть даже пустым, так как всю логику мы перехватим.
    // Но лучше оставить его таким для стандартного поведения.
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
    }
}