package com.loracore.item;

import com.loracore.component.ModComponents;
import com.loracore.component.data.RamData;
import net.minecraft.client.item.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class RamItem extends Item {
    public RamItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        RamData data = stack.get(ModComponents.RAM_DATA);
        if (data != null) {
            tooltip.add(Text.translatable("tooltip.loracore.capacity_kb", data.sizeKb()).formatted(Formatting.GRAY));
        }
    }
}