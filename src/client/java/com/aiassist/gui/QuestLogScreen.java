package com.aiassist.gui;

import com.aiassist.component.ModComponents;
import com.aiassist.component.PlayerQuestComponent;
import com.aiassist.quest.Quest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText; // ИМПОРТ
import net.minecraft.text.Text;

import java.util.List;

public class QuestLogScreen extends Screen {

    public QuestLogScreen() {
        super(Text.literal("Журнал заданий"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        PlayerQuestComponent questComponent = ModComponents.PLAYER_QUEST.get(MinecraftClient.getInstance().player);
        List<Quest> quests = questComponent.getQuests();

        if (quests.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "У вас нет активных заданий.", this.width / 2, this.height / 2, 0xA0A0A0);
            return;
        }

        int y = 40;
        int maxTextWidth = this.width - 40;

        for (Quest quest : quests) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§e" + quest.title()), 20, y, 0xFFFF55);
            y += 12;

            // ИСПРАВЛЕНИЕ: Метод wrapLines вызывается напрямую у textRenderer, а не через getTextHandler
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(Text.literal(quest.description()), maxTextWidth);
            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, line, 20, y, 0xFFFFFF);
                y += 10;
            }
            y += 5;

            String goalText = String.format("Цель: Принести %s (%d шт.)", quest.goal().item().getName().getString(), quest.goal().requiredAmount());
            context.drawTextWithShadow(this.textRenderer, Text.literal(goalText), 20, y, 0xAAAAAA);
            y += 12;

            String rewardText = String.format("Награда: %s (%d шт.)", quest.reward().item().getName().getString(), quest.reward().amount());
            context.drawTextWithShadow(this.textRenderer, Text.literal(rewardText), 20, y, 0x55FF55);
            y += 20;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}