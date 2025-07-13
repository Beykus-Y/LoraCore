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
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class VillagerDialogueScreen extends Screen {
    private final VillagerEntity villager;
    private final VillagerDataComponent villagerData;
    private final PlayerDialogueComponent playerDialogue;
    private final PlayerQuestComponent playerQuest;

    // Поля для состояния прокрутки
    private double scrollAmount;
    private int visibleHistoryHeight;
    private int totalHistoryHeight;

    private TextFieldWidget inputBox;
    private ButtonWidget sendButton;
    private ButtonWidget acceptQuestButton;
    private ButtonWidget declineQuestButton;
    private ButtonWidget completeQuestButton;

    public VillagerDialogueScreen(VillagerEntity villager) {
        super(Text.translatable("gui.loracore.dialogue.title_prefix", ModComponents.VILLAGER_DATA.get(villager).getVillagerName()));
        this.villager = villager;
        this.villagerData = ModComponents.VILLAGER_DATA.get(villager);
        this.playerDialogue = ModComponents.PLAYER_DIALOGUE.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
        this.playerQuest = ModComponents.PLAYER_QUEST.get(Objects.requireNonNull(MinecraftClient.getInstance().player));
    }

    @Override
    protected void init() {
        super.init();

        final int chatAreaY = 35;
        final int chatAreaBottomMargin = 45;
        this.visibleHistoryHeight = this.height - chatAreaY - chatAreaBottomMargin;

        this.inputBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, this.height - 38, 240, 20, Text.translatable("gui.loracore.dialogue.input_placeholder"));
        this.inputBox.setMaxLength(256);
        this.addDrawableChild(this.inputBox);

        this.sendButton = ButtonWidget.builder(Text.translatable("gui.loracore.dialogue.button.send"), button -> this.sendMessage())
                .dimensions(this.width / 2 + 95, this.height - 38, 55, 20)
                .build();
        this.addDrawableChild(sendButton);

        this.acceptQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.accept"), button -> {
            String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            if (this.client != null && this.client.player != null) {
                ClientPlayNetworking.send(new AcceptQuestC2SPacket(this.villager.getUuid(), langCode));
            }
        }).dimensions(this.width / 2 - 105, this.height - 38, 100, 20).build();
        this.addDrawableChild(acceptQuestButton);

        this.declineQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.decline"), button -> {
            if (this.client != null && this.client.player != null) {
                ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), "Я, пожалуй, откажусь.", "ru_ru"));
            }
        }).dimensions(this.width / 2 + 5, this.height - 38, 100, 20).build();
        this.addDrawableChild(declineQuestButton);

        this.completeQuestButton = ButtonWidget.builder(Text.translatable("gui.loracore.quest.button.complete"), button -> {
            ClientPlayNetworking.send(new CompleteQuestC2SPacket(this.villager.getUuid()));
        }).dimensions(this.width / 2 - 152, this.height - 38, 304, 20).build();
        this.addDrawableChild(completeQuestButton);

        ClientPlayNetworking.send(new SetVillagerFrozenC2SPacket(this.villager.getUuid(), true));
        updateButtonVisibility();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (this.inputBox.isActive() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            this.sendMessage();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, this.totalHistoryHeight - this.visibleHistoryHeight);
        this.scrollAmount = MathHelper.clamp(this.scrollAmount - verticalAmount * 10, 0, maxScroll);
        return true;
    }

    private void updateButtonVisibility() {
        if (this.client == null || this.client.player == null) return;

        Quest activeQuest = getActiveQuestForThisVillager();
        boolean hasOfferedQuest = villagerData.hasQuestForPlayer(this.client.player.getUuid());

        if (activeQuest != null) {
            boolean canComplete = canCompleteQuest(activeQuest);
            completeQuestButton.visible = canComplete;
            completeQuestButton.active = canComplete;
            inputBox.visible = !canComplete;
            sendButton.visible = !canComplete;
            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
        } else if (hasOfferedQuest) {
            inputBox.visible = false;
            sendButton.visible = false;
            acceptQuestButton.visible = true;
            acceptQuestButton.active = true;
            declineQuestButton.visible = true;
            declineQuestButton.active = true;
            completeQuestButton.visible = false;
        } else {
            inputBox.visible = true;
            sendButton.visible = true;
            acceptQuestButton.visible = false;
            declineQuestButton.visible = false;
            completeQuestButton.visible = false;
        }
    }

    private Quest getActiveQuestForThisVillager() {
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
        if (this.client != null && this.client.player != null) {
            ClientPlayNetworking.send(new SendDialogueMessageC2SPacket(this.villager.getUuid(), messageText, langCode));
        }
        this.inputBox.setText("");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateButtonVisibility();

        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        final int chatAreaX = 30;
        final int chatAreaY = 35;
        final int chatAreaWidth = this.width - 60;
        final int chatAreaHeight = this.visibleHistoryHeight + 5;

        context.getMatrices().push();
        context.enableScissor(chatAreaX, chatAreaY, chatAreaX + chatAreaWidth, chatAreaY + chatAreaHeight);

        List<Message> history = playerDialogue.getDialogueHistory(this.villager.getUuid());
        int y = chatAreaY - (int)scrollAmount;
        int currentTotalHeight = 0;

        for (Message msg : history) {
            if ("system".equals(msg.role())) continue;

            Text prefix = msg.role().equals("user")
                    ? Text.translatable("gui.loracore.dialogue.prefix.user")
                    : Text.literal(villagerData.getVillagerName() + ": ");

            MutableText fullText = Text.empty().append(prefix).append(msg.content());
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(fullText, chatAreaWidth);

            for (OrderedText line : wrappedLines) {
                if (y >= chatAreaY - 10 && y < chatAreaY + chatAreaHeight) {
                    context.drawTextWithShadow(this.textRenderer, line, chatAreaX, y, 0xFFFFFF);
                }
                y += 12;
                currentTotalHeight += 12;
            }
            y += 8;
            currentTotalHeight += 8;
        }

        this.totalHistoryHeight = currentTotalHeight;

        context.disableScissor();
        context.getMatrices().pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}