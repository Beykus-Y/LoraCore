package com.loracore.item;

import com.loracore.component.ModComponents;
import com.loracore.component.data.CpuData;
import net.minecraft.client.item.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class CpuItem extends Item {
    public CpuItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        CpuData data = stack.get(ModComponents.CPU_DATA);
        if (data != null) {
            tooltip.add(Text.translatable("tooltip.loracore.architecture", data.architecture()).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tooltip.loracore.frequency", data.frequencyMhz()).formatted(Formatting.GRAY));
        }
    }
}