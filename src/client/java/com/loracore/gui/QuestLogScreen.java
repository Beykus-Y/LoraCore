package com.loracore.gui;

import com.loracore.component.ModComponents;
import com.loracore.component.PlayerQuestComponent;
import com.loracore.quest.Quest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class QuestLogScreen extends Screen {

    public QuestLogScreen() {
        super(Text.translatable("gui.loracore.quest_log.title"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        PlayerQuestComponent questComponent = ModComponents.PLAYER_QUEST.get(MinecraftClient.getInstance().player);
        List<Quest> quests = questComponent.getQuests();

        if (quests.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.loracore.quest_log.no_quests"), this.width / 2, this.height / 2, 0xA0A0A0);
            return;
        }

        int y = 40;
        int maxTextWidth = this.width - 40;

        for (Quest quest : quests) {
            // Название и описание теперь используют ключи, заданные в Quest.java
            context.drawTextWithShadow(this.textRenderer, Text.translatable(quest.title()).formatted(Formatting.YELLOW), 20, y, 0xFFFF55);
            y += 12;

            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(Text.translatable(quest.description()), maxTextWidth);
            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, line, 20, y, 0xFFFFFF);
                y += 10;
            }
            y += 5;

            // Используем Text.translatable для цели и награды
            Text goalText = Text.translatable("gui.loracore.quest_log.goal", quest.goal().item().getName(), quest.goal().requiredAmount());
            context.drawTextWithShadow(this.textRenderer, goalText, 20, y, 0xAAAAAA);
            y += 12;

            Text rewardText = Text.translatable("gui.loracore.quest_log.reward", quest.reward().item().getName(), quest.reward().amount());
            context.drawTextWithShadow(this.textRenderer, rewardText, 20, y, 0x55FF55);
            y += 20;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}