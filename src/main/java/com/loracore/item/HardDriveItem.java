package com.loracore.item;

import com.loracore.component.ModComponents;
import com.loracore.component.data.StorageData;
import net.minecraft.client.item.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID; // Импортируем UUID

public class HardDriveItem extends Item {
    public HardDriveItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        StorageData data = stack.get(ModComponents.STORAGE_DATA);
        if (data != null) {
            tooltip.add(Text.translatable("tooltip.loracore.capacity_kb", data.capacityKb()).formatted(Formatting.GRAY));
            // Информация об UUID здесь больше недоступна, ее можно просто убрать
            tooltip.add(Text.translatable("tooltip.loracore.unformatted_info").formatted(Formatting.DARK_GRAY));
        }
    }
}