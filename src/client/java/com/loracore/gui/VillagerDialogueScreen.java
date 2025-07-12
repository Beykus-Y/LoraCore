package com.loracore.gui;

import com.loracore.api.dto.OpenAiApiDto.Message;
import com.loracore.component.ModComponents;
import com.loracore.component.PlayerDialogueComponent;
import com.loracore.component.PlayerQuestComponent;
import com.loracore.component.VillagerDataComponent;
import com.loracore.network.AcceptQuestC2SPacket;
import com.loracore.network.CompleteQuestC2SPacket;
import com.loracore.network.SendDialogueMessageC2SPacket;
import com.loracore.network.SetVillagerFrozenC2SPacket;
import com.loracore.quest.Quest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class VillagerDialogueScreen extends Screen {
    private final VillagerEntity villager;
    private final VillagerDataComponent villagerData;
    private final PlayerDialogueComponent playerDialogue;
    private final PlayerQuestComponent playerQuest;

    private TextFieldWidget inputBox;
    private ButtonWidget sendButton;
    private ButtonWidget acceptQuestButton;
    private ButtonWidget declineQuestButton;
    private ButtonWidget completeQuestButton;

    public VillagerDialogueScreen(VillagerEntity villager) {
        super(Text.translatable("gui.loracore.dialogue.title_prefix", ModComponents.VILLAGER_DATA.get(villager).getVillagerName()));
        this.villager = villager;
        // Получаем компоненты, которые автоматически синхронизируются с сервером
        this.villagerData = ModComponents.VILLAGER_DATA.get(villager);
        this.playerDialogue = ModComponents.PLAYER_DIALOGUE.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
        this.playerQuest = ModComponents.PLAYER_QUEST.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
    }

    @Override
    protected void init() {
        super.init();

        // Поле ввода для чата
        this.inputBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, this.height - 38, 240, 20, Text.translatable("gui.loracore.dialogue.input_placeholder"));
        this.inputBox.setMaxLength(256);
        this.addDrawableChild(this.inputBox);

        // Кнопка "Отправить"
        this.sendButton = ButtonWidget.builder(Text.translatable("gui.loracore.dialogue.button.send"), button -> this.sendMessage())
                .dimensions(this.width / 2 + 95, this.height - 38, 55, 20)
                .build();
        this.addDrawableChild(sendButton);

        // Кнопка "Принять квест"
        this.acceptQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.accept"), button -> {
            String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            if (this.client != null && this.client.player != null) {
                ClientPlayNetworking.send(new AcceptQuestC2SPacket(this.villager.getUuid(), langCode));
            }
        }).dimensions(this.width / 2 - 105, this.height - 38, 100, 20).build();
        this.addDrawableChild(acceptQuestButton);

        // Кнопка "Отказаться"
        this.declineQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.decline"), button -> {
            // ПРИМЕЧАНИЕ: Для отказа от квеста необходимо создать новый пакет, например, DeclineQuestC2SPacket,
            // и обработать его на сервере, чтобы удалить предложенный квест из VillagerDataComponent.
            // В качестве временного решения можно отправить обычное сообщение, но это менее надежно.
            // Здесь мы симулируем отправку сообщения об отказе.
            if (this.client != null && this.client.player != null) {
                ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), "Я, пожалуй, откажусь.", "ru_ru"));
            }
        }).dimensions(this.width / 2 + 5, this.height - 38, 100, 20).build();
        this.addDrawableChild(declineQuestButton);

        // Кнопка "Завершить квест"
        this.completeQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.complete"), button -> {
            ClientPlayNetworking.send(new CompleteQuestC2SPacket(this.villager.getUuid()));
        }).dimensions(this.width / 2 - 152, this.height - 38, 304, 20).build();
        this.addDrawableChild(completeQuestButton);

        // Сообщаем серверу, чтобы житель "замер" на время диалога
        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), true));
        // Обновляем видимость кнопок при открытии экрана
        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (this.client == null || this.client.player == null) return;

        Quest activeQuest = getActiveQuestForThisVillager();
        // Проверяем, есть ли ПРЕДЛОЖЕННЫЙ квест в компоненте жителя
        boolean hasOfferedQuest = villagerData.hasQuestForPlayer(this.client.player.getUuid());

        // Сценарий 1: У игрока есть АКТИВНЫЙ (принятый) квест от этого жителя
        if (activeQuest != null) {
            boolean canComplete = canCompleteQuest(activeQuest);
            completeQuestButton.visible = canComplete;
            completeQuestButton.active = canComplete;

            inputBox.visible = !canComplete;
            sendButton.visible = !canComplete;

            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
        }
        // Сценарий 2: У игрока нет активного квеста, но житель ПРЕДЛАГАЕТ новый
        else if (hasOfferedQuest) {
            inputBox.visible = false;
            sendButton.visible = false;

            acceptQuestButton.visible = true;
            acceptQuestButton.active = true;
            declineQuestButton.visible = true;
            declineQuestButton.active = true;

            completeQuestButton.visible = false;
        }
        // Сценарий 3: Обычный режим диалога без квестов
        else {
            inputBox.visible = true;
            sendButton.visible = true;

            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
            completeQuestButton.visible = false;
        }
    }

    private Quest getActiveQuestForThisVillager() {
        if (this.client == null || this.client.player == null) return null;
        // Ищем активный квест в компоненте игрока, который был выдан именно этим жителем
        return playerQuest.getQuests().stream()
                .filter(quest -> quest.villagerGiverUuid().equals(this.villager.getUuid()))
                .findFirst()
                .orElse(null);
    }

    private boolean canCompleteQuest(Quest quest) {
        if (quest == null || this.client == null || this.client.player == null) return false;
        // Проверяем, достаточно ли у игрока предметов для сдачи квеста
        return this.client.player.getInventory().count(quest.goal().item()) >= quest.goal().requiredAmount();
    }

    @Override
    public void close() {
        // "Отпускаем" жителя, когда диалог закрыт
        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), false));
        super.close();
    }

    private void sendMessage() {
        String messageText = this.inputBox.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        if (this.client != null && this.client.player != null) {
            ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), messageText, langCode));
        }
        this.inputBox.setText("");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Обновляем видимость кнопок каждый кадр, чтобы UI мгновенно реагировал на изменения
        // в компонентах после их синхронизации с сервером.
        updateButtonVisibility();

        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        List<Message> history = playerDialogue.getDialogueHistory(this.villager.getUuid());
        int y = 35;
        int maxTextWidth = this.width - 60;

        // Логика рендеринга диалога теперь проста: просто выводим историю сообщений
        // без какого-либо парсинга строк на клиенте.
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            // Пропускаем системные промпты (если они есть)
            if (msg.role().equals("system")) continue;

            Text prefix = msg.role().equals("user")
                    ? Text.translatable("gui.loracore.dialogue.prefix.user")
                    : Text.literal(villagerData.getVillagerName() + ": ");

            MutableText fullText = Text.empty().append(prefix).append(msg.content());
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(fullText, maxTextWidth);

            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, line, 30, y, 0xFFFFFF);
                y += 12;
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}