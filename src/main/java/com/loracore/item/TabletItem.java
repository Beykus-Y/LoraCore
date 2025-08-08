// Полный исправленный файл: src/main/java/com/loracore/item/TabletItem.java
package com.loracore.item;

import com.loracore.component.ModComponents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.UUID;

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

    /**
     * Этот метод вызывается каждый тик, пока предмет находится в инвентаре игрока.
     * Мы используем его, чтобы гарантировать, что у планшета есть UUID.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // Выполняем логику только на сервере
        if (!world.isClient()) {
            // Проверяем, есть ли у предмета наш Data Component. Если нет - присваиваем.
            if (!stack.contains(ModComponents.TABLET_UUID)) {
                stack.set(ModComponents.TABLET_UUID, UUID.randomUUID());
            }
        }
    }
}