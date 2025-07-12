package com.loracore.gui;

import com.loracore.ModConfigScreen;
import com.loracore.api.dto.OpenAiApiDto;
import com.loracore.component.ModComponents;
import com.loracore.component.PlayerAskHistoryComponent;
import com.loracore.network.AskAiC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Objects;

public class AskChatScreen extends Screen {
    private static final Identifier SETTINGS_ICON_TEXTURE = new Identifier("loracore", "textures/gui/settings_icon.png");

    private TextFieldWidget inputBox;
    private double scrollAmount;
    private int visibleHistoryHeight;
    private int totalHistoryHeight;

    public AskChatScreen() {
        super(Text.translatable("gui.loracore.ask.title"));
    }

    @Override
    protected void init() {
        super.init();
        Objects.requireNonNull(this.client);

        int chatAreaY = 32;
        int chatAreaHeight = this.height - chatAreaY - 40;
        this.visibleHistoryHeight = chatAreaHeight - 10;

        inputBox = new TextFieldWidget(textRenderer, this.width / 2 - 150, this.height - 28, 260, 20, Text.translatable("gui.loracore.ask.input_placeholder"));
        inputBox.setMaxLength(256);
        addDrawableChild(inputBox);

        ButtonWidget sendButton = ButtonWidget.builder(Text.translatable("gui.loracore.dialogue.button.send"), button -> this.sendMessage())
                .dimensions(this.width / 2 + 115, this.height - 28, 40, 20)
                .build();
        addDrawableChild(sendButton);

        TexturedButtonWidget settingsButton = new TexturedButtonWidget(
                this.width - 25, 5, 20, 20,
                new ButtonTextures(SETTINGS_ICON_TEXTURE, SETTINGS_ICON_TEXTURE),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(ModConfigScreen.create(this));
                    }
                }
        );
        addDrawableChild(settingsButton);

        setInitialFocus(inputBox);
    }

    private void sendMessage() {
        String question = inputBox.getText().trim();
        if (!question.isEmpty() && client != null && client.player != null) {
            String langCode = client.getLanguageManager().getLanguage();
            ClientPlayNetworking.send(new AskAiC2SPacket(question, langCode));
            inputBox.setText("");
            this.scrollAmount = Math.max(0, this.totalHistoryHeight - this.visibleHistoryHeight);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.inputBox.isActive() && (keyCode == 257 || keyCode == 335)) { // ENTER or KP_ENTER
            sendMessage();
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        int chatAreaX = 20;
        int chatAreaY = 32;
        int chatAreaWidth = this.width - 40;
        int chatAreaHeight = this.height - chatAreaY - 40;

        context.getMatrices().push();
        context.enableScissor(chatAreaX, chatAreaY, chatAreaX + chatAreaWidth, chatAreaY + chatAreaHeight);
        renderHistory(context);
        context.disableScissor();
        context.getMatrices().pop();
    }

    private void renderHistory(DrawContext context) {
        if (client == null || client.player == null) return;

        PlayerAskHistoryComponent historyComponent = ModComponents.PLAYER_ASK_HISTORY.get(client.player);
        List<OpenAiApiDto.Message> history = historyComponent.getHistory();

        int y = 37 - (int)scrollAmount;
        int x = 25;
        int textWidth = this.width - 50;
        int totalHeight = 0;

        for (OpenAiApiDto.Message message : history) {
            if ("system".equals(message.role())) continue;

            Text prefix = Text.literal(message.role().equals("user") ? "Вы: " : "AI: ").formatted(message.role().equals("user") ? Formatting.YELLOW : Formatting.AQUA);

            // ИЗМЕНЕНИЕ: Используем парсер для форматирования текста ответа
            Text content = parseFormattedText(message.content());

            Text fullText = Text.empty().append(prefix).append(content);

            List<OrderedText> wrappedLines = textRenderer.wrapLines(fullText, textWidth);

            for (OrderedText line : wrappedLines) {
                if (y >= 32 && y < this.height - 40) {
                    context.drawTextWithShadow(textRenderer, line, x, y, 0xFFFFFF);
                }
                y += 12;
                totalHeight += 12;
            }
            y += 6;
            totalHeight += 6;
        }

        this.totalHistoryHeight = totalHeight;
    }

    /**
     * Простой парсер, который преобразует **жирный** текст в отформатированный Text.
     * @param content Строка с Markdown-подобной разметкой.
     * @return Объект Text с примененными стилями.
     */
    private static Text parseFormattedText(String content) {
        MutableText resultText = Text.empty();
        // Мы разбиваем строку по "**". Части с нечетными индексами (1, 3, 5...) будут жирными.
        // Используем "\\*\\*" для экранирования символа '*' в регулярном выражении.
        String[] parts = content.split("\\*\\*");

        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) {
                // Это жирная часть
                resultText.append(Text.literal(parts[i]).formatted(Formatting.BOLD));
            } else {
                // Это обычная часть
                resultText.append(Text.literal(parts[i]));
            }
        }
        return resultText;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}