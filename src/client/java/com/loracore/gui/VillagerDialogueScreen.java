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
import java.util.Objects; // Добавлен импорт Objects
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

    private boolean isQuestOfferActive = false;

    public VillagerDialogueScreen(VillagerEntity villager) {
        super(Text.translatable("gui.loracore.dialogue.title_prefix", ModComponents.VILLAGER_DATA.get(villager).getVillagerName()));
        this.villager = villager;
        this.villagerData = ModComponents.VILLAGER_DATA.get(villager);
        // Исправление: Используем Objects.requireNonNull для уверенности, что player не null
        // Это безопасно, так как экран диалога открывается только когда игрок существует.
        this.playerDialogue = ModComponents.PLAYER_DIALOGUE.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
        this.playerQuest = ModComponents.PLAYER_QUEST.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
    }

    @Override
    protected void init() {
        super.init();

        this.inputBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, this.height - 38, 240, 20, Text.translatable("gui.loracore.dialogue.input_placeholder"));
        this.addDrawableChild(this.inputBox);

        this.sendButton = ButtonWidget.builder(Text.translatable("gui.loracore.dialogue.button.send"), button -> this.sendMessage())
                .dimensions(this.width / 2 + 95, this.height - 38, 55, 20)
                .build();
        this.addDrawableChild(sendButton);

        this.acceptQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.accept"), button -> {
            String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            // Исправление: Проверяем client.player на null, хотя в этом контексте он почти всегда есть
            if (this.client != null && this.client.player != null) {
                ClientPlayNetworking.send(new AcceptQuestC2SPacket(this.villager.getUuid(), langCode));
            }
            this.setQuestOfferMode(false);
        }).dimensions(this.width / 2 - 105, this.height - 38, 100, 20).build();
        this.addDrawableChild(acceptQuestButton);

        this.declineQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.decline"), button -> {
            // Исправление: Проверяем client.player на null
            if (this.client != null && this.client.player != null) {
                UUID playerUuid = this.client.player.getUuid();
                if (villagerData.hasQuestForPlayer(playerUuid)) {
                    villagerData.completeQuestForPlayer(playerUuid); // Используем completeQuestForPlayer для удаления "предложенного" квеста
                }
                this.playerDialogue.addMessageToHistory(this.villager.getUuid(), new Message("assistant", "Очень жаль. Может, в другой раз."));
                // Сброс флага, кнопки обновятся при следующем рендере после синхронизации компонента
                this.setQuestOfferMode(false);
            }
        }).dimensions(this.width / 2 + 5, this.height - 38, 100, 20).build();
        this.addDrawableChild(declineQuestButton);

        this.completeQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.complete"), button -> {
            ClientPlayNetworking.send(new CompleteQuestC2SPacket(this.villager.getUuid()));
            // Квест будет удален сервером, компонент синхронизируется, и кнопки обновятся
        }).dimensions(this.width / 2 - 152, this.height - 38, 304, 20).build();
        this.addDrawableChild(completeQuestButton);

        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), true));
        updateButtonVisibility(); // Первоначальное обновление видимости при открытии экрана
    }

    private void setQuestOfferMode(boolean isQuestOffer) {
        this.isQuestOfferActive = isQuestOffer;
        // updateButtonVisibility() будет вызван в render()
    }

    private void updateButtonVisibility() {
        Quest activeQuest = getActiveQuestForThisVillager();

        if (activeQuest != null) {
            boolean canComplete = canCompleteQuest(activeQuest);
            // Используем поле 'visible' вместо метода 'setVisible()'
            completeQuestButton.visible = canComplete;
            completeQuestButton.active = canComplete; // active влияет на возможность нажатия

            inputBox.visible = !canComplete;
            sendButton.visible = !canComplete;

            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
        } else if (isQuestOfferActive) {
            inputBox.visible = false;
            sendButton.visible = false;

            acceptQuestButton.visible = true;
            declineQuestButton.visible = true;

            completeQuestButton.visible = false;
        } else {
            // Обычный режим диалога
            inputBox.visible = true;
            sendButton.visible = true;

            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
            completeQuestButton.visible = false;
        }
    }

    private Quest getActiveQuestForThisVillager() {
        // На клиенте для определения активного квеста, назначенного именно ЭТИМ жителем
        // мы смотрим в список квестов игрока, а не в VillagerDataComponent.
        // VillagerDataComponent на сервере будет хранить квест, который житель "предложил"
        // или "назначил" игроку, но на клиенте игрок должен видеть только свои активные квесты
        // из PlayerQuestComponent.
        // Исправление: Дополнительная проверка на null для player
        if (this.client == null || this.client.player == null) return null;
        return playerQuest.getQuests().stream()
                .filter(quest -> quest.villagerGiverUuid().equals(this.villager.getUuid()))
                .findFirst()
                .orElse(null);
    }

    private boolean canCompleteQuest(Quest quest) {
        if (quest == null || this.client == null || this.client.player == null) return false;
        return this.client.player.getInventory().count(quest.goal().item()) >= quest.goal().requiredAmount();
    }

    @Override
    public void close() {
        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), false));
        super.close();
    }

    private void sendMessage() {
        String messageText = this.inputBox.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        // Исправление: Проверяем client.player на null
        if (this.client != null && this.client.player != null) {
            ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), messageText, langCode));
        }
        this.inputBox.setText("");
        setQuestOfferMode(false); // Сброс режима предложения квеста после отправки обычного сообщения
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        List<Message> history = playerDialogue.getDialogueHistory(this.villager.getUuid());
        int y = 35;
        int maxTextWidth = this.width - 60;

        // Переменная для определения, является ли последнее сообщение предложением квеста
        boolean currentLastMessageIsQuestOffer = false;

        // Начинаем с 1, чтобы пропустить системный промпт
        for (int i = 1; i < history.size(); i++) {
            Message msg = history.get(i);

            String content = msg.content();
            // Проверяем, есть ли маркер квеста.
            if (msg.role().equals("assistant") && content.endsWith("[QUEST_OFFER]")) {
                content = content.replace("[QUEST_OFFER]", "").trim();
                // Если это последнее сообщение в истории и оно содержит маркер квеста,
                // и при этом у игрока нет активного квеста от этого жителя,
                // то текущее состояние - это активное предложение квеста.
                if (i == history.size() - 1 && getActiveQuestForThisVillager() == null) {
                    currentLastMessageIsQuestOffer = true;
                }
            }

            Text prefix = msg.role().equals("user")
                    ? Text.translatable("gui.loracore.dialogue.prefix.user")
                    : Text.literal(villagerData.getVillagerName() + ": ");

            MutableText fullText = Text.empty().append(prefix).append(content);
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(fullText, maxTextWidth);

            for (OrderedText line : wrappedLines) {
                context.drawTextWithShadow(this.textRenderer, line, 30, y, 0xFFFFFF);
                y += 12;
            }
        }

        // Исправление для "effectively final": создаем финальную копию переменной
        final boolean finalCurrentLastMessageIsQuestOffer = currentLastMessageIsQuestOffer;

        // Устанавливаем режим предложения квеста ВНЕ цикла, на основе последнего сообщения
        // и только если у игрока нет активного квеста от этого жителя.
        // Вызов setQuestOfferMode() внутри client.execute() необходим, чтобы изменения состояния
        // GUI происходили на основном потоке Minecraft после завершения текущего рендера.
        // Исправление: Проверяем this.client на null перед вызовом execute
        if (this.isQuestOfferActive != finalCurrentLastMessageIsQuestOffer && this.client != null) { // Избегаем ненужных вызовов
            this.client.execute(() -> setQuestOfferMode(finalCurrentLastMessageIsQuestOffer));
        }

        // ОБЯЗАТЕЛЬНО: Вызываем updateButtonVisibility() каждый кадр,
        // чтобы GUI реагировал на изменения в компонентах после синхронизации.
        // Теперь updateButtonVisibility будет использовать актуальное isQuestOfferActive
        // и состояние квеста из компонента.
        if (this.client != null) {
            this.client.execute(this::updateButtonVisibility);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}