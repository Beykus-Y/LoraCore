package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.ui.layout.VerticalLayout;
import com.lora.tabletos.ui.widgets.LabelWidget;
import com.lora.tabletos.ui.widgets.ScrollPane;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Обновленный AI Assistant с использованием системы Layouts.
 */
public class AiAssistantApp implements IApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiAssistantApp.class);

    private IApplicationApi api;

    // UI Компоненты
    private ScrollPane chatScroll;
    private VerticalLayout messageList;

    // Логика ввода
    private String currentInput = "";
    private boolean isTyping = false;
    private CompletableFuture<String> currentAiResponse = null;

    // Цвета
    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;
    private static final int INPUT_BG_COLOR = 0xFF2D2D2D;
    private static final int USER_MSG_COLOR = 0xFF2196F3; // Голубой
    private static final int AI_MSG_COLOR = 0xFF4CAF50;   // Зеленый
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        int[] size = api.getScreenSize();
        int width = size[0];
        // Высота экрана минус статус-бар (20) и поле ввода (30)
        int chatHeight = size[1] - 20 - 30;

        // 1. Создаем контейнер для списка сообщений
        // Ширина чуть меньше экрана, чтобы влез скроллбар
        this.messageList = new VerticalLayout(0, 0, width - 10);
        this.messageList.setPadding(10);
        this.messageList.setSpacing(5); // Отступ между сообщениями

        // 2. Создаем панель прокрутки и кладем в нее список
        this.chatScroll = new ScrollPane(0, 0, width, chatHeight);
        this.chatScroll.setContent(this.messageList);

        // Приветствие
        addMessage("AI Assistant", "Привет! Я использую новую систему Layouts. Напиши мне что-нибудь.", true);
    }

    private void addMessage(String sender, String content, boolean isAi) {
        // Создаем виджет текста
        int color = isAi ? AI_MSG_COLOR : USER_MSG_COLOR;
        String prefix = isAi ? "[AI] " : "[YOU] ";

        // LabelWidget теперь просто добавляется в лейаут.
        // Нам не нужно считать Y координату!
        LabelWidget label = new LabelWidget(0, 0, prefix + content, color);
        messageList.addWidget(label);

        // Можно добавить пустую строку для отступа (костыль, пока нет margin)
        // messageList.addWidget(new LabelWidget(0,0, "", 0));
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] size = api.getScreenSize();
        int width = size[0];
        int height = size[1];
        int inputY = height - 30;

        // 1. Рисуем фон всего приложения
        g.fill(0, 0, width, height, BACKGROUND_COLOR);

        // 2. Рисуем скролл с сообщениями
        // Важно: ScrollPane сам обрежет лишнее (scissor) и сдвинет контент
        chatScroll.render(g, mouseX, mouseY);

        // 3. Рисуем поле ввода внизу (фиксированное)
        g.fill(0, inputY, width, height, INPUT_BG_COLOR);
        g.fill(0, inputY, width, inputY + 1, 0xFF555555); // Линия разделитель

        String prompt = "Запрос: " + currentInput + (isTyping ? "_" : "");
        g.drawString(prompt, 10, inputY + 10, TEXT_COLOR);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        // Сначала даем шанс скроллу обработать событие (прокрутка колесиком)
        if (chatScroll.onEvent(event)) {
            return true;
        }

        switch (event) {
            case KernelEvent.KeyPressed keyEvent -> {
                if (keyEvent.keyCode == 256) { // ESC
                    onClose();
                    return true;
                }
                if (keyEvent.keyCode == 257) { // Enter
                    sendMessage();
                    return true;
                }
                if (keyEvent.keyCode == 259) { // Backspace
                    if (!currentInput.isEmpty()) {
                        currentInput = currentInput.substring(0, currentInput.length() - 1);
                    }
                    return true;
                }
                // Ввод символов лучше обрабатывать в CharTyped, но для простоты оставим здесь основные
            }
            case KernelEvent.CharTyped charEvent -> {
                currentInput += charEvent.chr;
                isTyping = true;
                return true;
            }
            default -> {}
        }
        return false;
    }

    private void sendMessage() {
        if (currentInput.trim().isEmpty() || currentAiResponse != null) return;

        String userMessage = currentInput.trim();
        addMessage("You", userMessage, false);
        currentInput = "";

        // Показываем уведомление, что запрос ушел
        api.showNotification("Думаю...", false);

        currentAiResponse = api.askAI(userMessage);
        currentAiResponse.whenComplete((response, throwable) -> {
            // ВАЖНО: Обновление UI должно происходить в потоке рендера!
            api.runOnRenderThread(() -> {
                if (throwable != null) {
                    addMessage("System", "Ошибка: " + throwable.getMessage(), true);
                    api.showNotification("Ошибка сети", true);
                } else {
                    addMessage("AI", response, true);
                    // Уведомление о готовности
                    api.showNotification("Ответ получен", false);
                }
                currentAiResponse = null;
            });
        });
    }

    @Override
    public void onClose() {
        LOGGER.info("AI Assistant closing");
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
}