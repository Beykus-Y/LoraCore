// Полный исправленный файл: src/main/java/com/loracore/item/TabletItem.java
package com.loracore.item;

import com.loracore.component.ModComponents;
import com.loracore.component.data.MotherboardData;
import com.loracore.component.data.RamData;
import net.minecraft.client.item.TooltipType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
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

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        
        MotherboardData motherboard = stack.get(ModComponents.MOTHERBOARD_DATA);
        if (motherboard != null) {
            int totalRamKb = motherboard.ram().stream()
                    .mapToInt(ramStack -> {
                        RamData ramData = ramStack.get(ModComponents.RAM_DATA);
                        return ramData != null ? ramData.sizeKb() : 0;
                    })
                    .sum();
            
            if (totalRamKb > 0) {
                tooltip.add(Text.translatable("tooltip.loracore.total_ram_kb", totalRamKb)
                        .formatted(Formatting.AQUA));
            }
        }
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