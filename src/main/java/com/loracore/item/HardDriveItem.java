// Файл: src/main/java/com/loracore/item/HardDriveItem.java
package com.loracore.item;

import com.loracore.component.ModComponents;
import com.loracore.component.data.FileSystemsData;
import com.loracore.component.data.StorageData;
import net.minecraft.client.item.TooltipType;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class HardDriveItem extends Item {

    public HardDriveItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient()) {
            // ИЗМЕНЕНИЕ: Теперь мы просто создаем и устанавливаем один UUID
            if (!stack.contains(ModComponents.FILE_SYSTEMS_DATA)) {
                stack.set(ModComponents.FILE_SYSTEMS_DATA, new FileSystemsData(UUID.randomUUID()));
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        StorageData storageData = stack.get(ModComponents.STORAGE_DATA);
        if (storageData != null) {
            tooltip.add(Text.translatable("tooltip.loracore.capacity_kb", storageData.capacityKb()).formatted(Formatting.GRAY));
        }

        FileSystemsData fsData = stack.get(ModComponents.FILE_SYSTEMS_DATA);
        // ИЗМЕНЕНИЕ: Получаем UUID напрямую
        if (fsData != null) {
            tooltip.add(Text.literal("FS UUID: " + fsData.fsUuid().toString().substring(0, 13) + "...").formatted(Formatting.DARK_GRAY));
        }
    }
}