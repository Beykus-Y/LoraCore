package com.loracore.gui;

import com.loracore.component.ModComponents;
import com.loracore.component.data.MotherboardData;
import com.loracore.component.data.RamData;
import com.loracore.item.TabletItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран конфигурации планшета для управления компонентами.
 * Пока что только отображает информацию о компонентах.
 * В будущем здесь можно будет добавлять/удалять компоненты.
 */
public class TabletConfigScreen extends Screen {
    private final ItemStack tabletStack;
    private final Screen parent;

    public TabletConfigScreen(Screen parent, ItemStack tabletStack) {
        super(Text.translatable("gui.loracore.tablet_config.title"));
        this.parent = parent;
        this.tabletStack = tabletStack;
    }

    @Override
    protected void init() {
        super.init();

        // Кнопка закрытия
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                button -> this.close()
        ).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Заголовок
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        if (!(tabletStack.getItem() instanceof TabletItem)) {
            context.drawCenteredTextWithShadow(this.textRenderer, 
                Text.literal("Error: Not a tablet!").formatted(Formatting.RED), 
                this.width / 2, 50, 0xFFFFFF);
            return;
        }

        MotherboardData motherboard = tabletStack.get(ModComponents.MOTHERBOARD_DATA);
        if (motherboard == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Error: No motherboard data!").formatted(Formatting.RED),
                this.width / 2, 50, 0xFFFFFF);
            return;
        }

        int y = 50;

        // CPU Information
        context.drawTextWithShadow(this.textRenderer, Text.literal("CPU:").formatted(Formatting.YELLOW), 20, y, 0xFFFFFF);
        if (motherboard.cpu().isPresent()) {
            String cpuInfo = motherboard.cpu().get().get(ModComponents.CPU_DATA).architecture();
            context.drawTextWithShadow(this.textRenderer, Text.literal("  " + cpuInfo).formatted(Formatting.WHITE), 20, y + 12, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("  None").formatted(Formatting.GRAY), 20, y + 12, 0xFFFFFF);
        }
        y += 30;

        // GPU Information
        context.drawTextWithShadow(this.textRenderer, Text.literal("GPU:").formatted(Formatting.YELLOW), 20, y, 0xFFFFFF);
        if (motherboard.gpu().isPresent()) {
            String gpuInfo = motherboard.gpu().get().get(ModComponents.GPU_DATA).tier();
            context.drawTextWithShadow(this.textRenderer, Text.literal("  " + gpuInfo).formatted(Formatting.WHITE), 20, y + 12, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("  None").formatted(Formatting.GRAY), 20, y + 12, 0xFFFFFF);
        }
        y += 30;

        // RAM Information
        context.drawTextWithShadow(this.textRenderer, Text.literal("RAM:").formatted(Formatting.YELLOW), 20, y, 0xFFFFFF);
        List<ItemStack> ramModules = motherboard.ram();
        if (ramModules.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("  None").formatted(Formatting.GRAY), 20, y + 12, 0xFFFFFF);
        } else {
            int totalRam = 0;
            for (int i = 0; i < ramModules.size(); i++) {
                ItemStack ramStack = ramModules.get(i);
                RamData ramData = ramStack.get(ModComponents.RAM_DATA);
                if (ramData != null) {
                    totalRam += ramData.sizeKb();
                    context.drawTextWithShadow(this.textRenderer, 
                        Text.literal("  Slot " + (i + 1) + ": " + ramData.sizeKb() + " KB").formatted(Formatting.WHITE), 
                        20, y + 12 + i * 12, 0xFFFFFF);
                }
            }
            context.drawTextWithShadow(this.textRenderer, 
                Text.literal("  Total: " + totalRam + " KB").formatted(Formatting.AQUA), 
                20, y + 12 + ramModules.size() * 12, 0xFFFFFF);
        }
        y += 30 + ramModules.size() * 12;

        // Storage Information  
        context.drawTextWithShadow(this.textRenderer, Text.literal("Storage:").formatted(Formatting.YELLOW), 20, y, 0xFFFFFF);
        List<ItemStack> storageModules = motherboard.storage();
        if (storageModules.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("  None").formatted(Formatting.GRAY), 20, y + 12, 0xFFFFFF);
        } else {
            for (int i = 0; i < storageModules.size(); i++) {
                ItemStack storageStack = storageModules.get(i);
                int capacityKb = storageStack.get(ModComponents.STORAGE_DATA).capacityKb();
                int capacityMb = capacityKb / 1024; // Конвертируем KB в MB для отображения
                context.drawTextWithShadow(this.textRenderer, 
                    Text.literal("  Drive " + (i + 1) + ": " + capacityMb + " MB").formatted(Formatting.WHITE), 
                    20, y + 12 + i * 12, 0xFFFFFF);
            }
        }
        y += 30 + storageModules.size() * 12;

        // Firmware Information
        context.drawTextWithShadow(this.textRenderer, Text.literal("Firmware:").formatted(Formatting.YELLOW), 20, y, 0xFFFFFF);
        if (motherboard.firmware().isPresent()) {
            String firmwareInfo = motherboard.firmware().get().get(ModComponents.FIRMWARE_DATA).recoveryScript().toString();
            context.drawTextWithShadow(this.textRenderer, Text.literal("  " + firmwareInfo).formatted(Formatting.WHITE), 20, y + 12, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("  None").formatted(Formatting.GRAY), 20, y + 12, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
