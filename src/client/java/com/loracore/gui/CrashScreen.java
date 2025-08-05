package com.loracore.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CrashScreen extends Screen {

    private final String errorMessage;
    private final Screen parentScreen;

    // [НОВОЕ] Константы для внешнего вида, как в TabletScreen
    private static final int BACKGROUND_OVERLAY_COLOR = 0xB0000000;
    private static final int TERMINAL_BG_COLOR = 0xFF1E1E1E; // Темно-серый
    private static final int ERROR_TEXT_COLOR = 0xFFE0E0E0; // Светло-серый

    public CrashScreen(String errorMessage, Screen parentScreen) {
        super(Text.literal("VM Critical Error").formatted(Formatting.DARK_RED));
        this.errorMessage = errorMessage;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        // Располагаем кнопку внизу по центру
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.loracore.crash.close"), (button) -> {
                    if (this.client != null) {
                        this.client.setScreen(this.parentScreen);
                    }
                })
                .dimensions(this.width / 2 - 100, this.height - 38, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // [ПЕРЕРАБОТАНО]
        // 1. Рисуем полупрозрачный фон поверх мира
        this.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, BACKGROUND_OVERLAY_COLOR);

        // 2. Рисуем сплошной черный фон для "экрана"
        int screenMargin = 30;
        int screenX = screenMargin;
        int screenY = screenMargin;
        int screenWidth = this.width - screenMargin * 2;
        int screenHeight = this.height - screenMargin * 2 - 20; // Оставляем место для кнопки
        context.fill(screenX, screenY, screenX + screenWidth, screenY + screenHeight, TERMINAL_BG_COLOR);

        // 3. Рисуем заголовок ошибки
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, screenY + 10, 0xFFFFFF);

        // 4. Рисуем само сообщение об ошибке с переносом строк
        int textX = screenX + 10;
        int textY = screenY + 30;
        int wrapWidth = screenWidth - 20;

        int currentY = textY;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(this.errorMessage), wrapWidth)) {
            context.drawTextWithShadow(this.textRenderer, line, textX, currentY, ERROR_TEXT_COLOR);
            currentY += 12;
        }

        // 5. Рендерим виджеты (нашу кнопку)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
        }
    }
}