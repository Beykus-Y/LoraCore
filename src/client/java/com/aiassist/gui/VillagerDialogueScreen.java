package com.aiassist.gui;

import com.aiassist.api.dto.OpenAiApiDto.Message;
import com.aiassist.component.ModComponents;
import com.aiassist.component.PlayerDialogueComponent;
import com.aiassist.component.VillagerDataComponent;
import com.aiassist.network.SendDialogueMessageC2SPacket;
import com.aiassist.network.SetVillagerFrozenC2SPacket; // ИМПОРТ НОВОГО ПАКЕТА
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public class VillagerDialogueScreen extends Screen {
    private final VillagerEntity villager;
    private TextFieldWidget inputBox;
    private final VillagerDataComponent villagerData;
    private final PlayerDialogueComponent playerDialogue;

    public VillagerDialogueScreen(VillagerEntity villager) {
        super(Text.literal("Диалог с " + ModComponents.VILLAGER_DATA.get(villager).getVillagerName()));
        this.villager = villager;
        this.villagerData = ModComponents.VILLAGER_DATA.get(villager);
        this.playerDialogue = ModComponents.PLAYER_DIALOGUE.get(MinecraftClient.getInstance().player);
    }

    @Override
    protected void init() {
        super.init();
        this.inputBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, this.height - 38, 240, 20, Text.literal("Спросить что-нибудь..."));
        ButtonWidget sendButton = ButtonWidget.builder(Text.literal("Отправить"), button -> this.sendMessage())
                .dimensions(this.width / 2 + 95, this.height - 38, 55, 20)
                .build();

        this.addDrawableChild(this.inputBox);
        this.addDrawableChild(sendButton);

        // ИЗМЕНЕНИЕ: Отправляем пакет, чтобы "заморозить" жителя
        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), true));
    }

    @Override
    public void close() {
        // ИЗМЕНЕНИЕ: Отправляем пакет, чтобы "разморозить" жителя при закрытии экрана
        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), false));
        super.close();
    }

    private void sendMessage() {
        String messageText = this.inputBox.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), messageText));
        this.inputBox.setText("");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        List<Message> history = playerDialogue.getDialogueHistory(this.villager.getUuid());
        int y = 35;
        // Задаем максимальную ширину для текста диалога
        int maxTextWidth = this.width - 60; // Отступы по 30 пикселей с каждой стороны

        for (int i = 1; i < history.size(); i++) {
            Message msg = history.get(i);
            String prefix = msg.role().equals("user") ? "Вы: " : villagerData.getVillagerName() + ": ";
            String fullText = prefix + msg.content();

            // ИЗМЕНЕНИЕ: Разбиваем текст на строки, если он не помещается
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(Text.literal(fullText), maxTextWidth);

            // Рендерим каждую строку отдельно
            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, line, 30, y, 0xFFFFFF);
                y += 12; // Смещаем Y для следующей строки
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}