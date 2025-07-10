package com.loracore.gui;

import com.loracore.network.AskAiC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AskScreen extends Screen {
    private TextFieldWidget inputBox;
    private ButtonWidget sendButton;

    // Параметры панели
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 140;
    private static final int RADIUS = 12;

    public AskScreen() {
        super(Text.translatable("gui.aiassist.ask.title"));
    }

    @Override
    protected void init() {
        super.init();

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Поле ввода
        inputBox = new TextFieldWidget(
                textRenderer,
                cx - (PANEL_WIDTH / 2) + 16,
                cy - 10,
                PANEL_WIDTH - 32,
                20,
                Text.translatable("gui.aiassist.ask.input_placeholder")
        );
        // Оставляем стандартную обводку и фон
        addDrawableChild(inputBox);

        // Кнопка отправки
        sendButton = ButtonWidget.builder(
                        Text.translatable("gui.aiassist.dialogue.button.send"),
                        button -> this.sendMessage()
                )
                .dimensions(cx - 40, cy + 24, 80, 20)
                .build();
        addDrawableChild(sendButton);

        setInitialFocus(inputBox);
    }

    private void sendMessage() {
        String question = inputBox.getText().trim();
        if (!question.isEmpty()) {
            String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            ClientPlayNetworking.send(new AskAiC2SPacket(question, langCode));

            if (client != null && client.player != null) {
                client.player.sendMessage(
                        Text.translatable("command.aiassist.ask.success")
                                .formatted(Formatting.YELLOW),
                        false
                );
            }
            close();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Фон затемнённый
        ctx.fill(0, 0, width, height, 0xBB000000);

        int cx = width / 2;
        int cy = height / 2;
        int x0 = cx - PANEL_WIDTH / 2;
        int y0 = cy - PANEL_HEIGHT / 2;
        int x1 = x0 + PANEL_WIDTH;
        int y1 = y0 + PANEL_HEIGHT;

        // Тень
        ctx.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0x55000000);

        // Панель
        fillRounded(ctx, x0, y0, x1, y1, RADIUS, 0xFF1E1E2E);

        // Градиент заголовка
        ctx.fillGradient(x0, y0, x1, y0 + 24, 0xFF3F3F5F, 0xFF2B2B3B);

        // Текст заголовка
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, cx, y0 + 7, 0xFFFFFF);

        // Разделитель
        ctx.fill(x0 + 4, y0 + 24, x1 - 4, y0 + 25, 0xFF444460);

        // Рендер виджетов
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // Рисует «закруглённые» углы
    private void fillRounded(DrawContext ctx, int x0, int y0, int x1, int y1, int r, int color) {
        ctx.fill(x0 + r, y0, x1 - r, y1, color);
        ctx.fill(x0, y0 + r, x1, y1 - r, color);
        ctx.fill(x0, y0, x0 + r, y0 + r, color);
        ctx.fill(x1 - r, y0, x1, y0 + r, color);
        ctx.fill(x0, y1 - r, x0 + r, y1, color);
        ctx.fill(x1 - r, y1 - r, x1, y1, color);
    }
}
