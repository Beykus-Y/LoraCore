package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Приложение "AI Assistant" - флагманское приложение LoraCore OS.
 * Предоставляет интерфейс для взаимодействия с ИИ.
 */
public class AiAssistantApp implements IApplication {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiAssistantApp.class);
    
    private IApplicationApi api;
    private List<ChatMessage> chatHistory = new ArrayList<>();
    private String currentInput = "";
    private boolean isTyping = false;
    private CompletableFuture<String> currentAiResponse = null;
    
    // UI константы
    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;
    private static final int CHAT_BACKGROUND_COLOR = 0xFF2D2D2D;
    private static final int INPUT_BACKGROUND_COLOR = 0xFF3D3D3D;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int AI_TEXT_COLOR = 0xFF4CAF50;
    private static final int USER_TEXT_COLOR = 0xFF2196F3;
    
    private static final int PADDING = 10;
    private static final int CHAT_HEIGHT = 400;
    private static final int INPUT_HEIGHT = 40;
    
    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        LOGGER.info("AI Assistant app loaded");
        
        // Добавляем приветственное сообщение
        chatHistory.add(new ChatMessage("AI Assistant", "Привет! Я ваш ИИ-ассистент. Чем могу помочь?", true));
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] screenSize = api.getScreenSize();
        int screenWidth = screenSize[0];
        int screenHeight = screenSize[1];
        
        // Рисуем фон
        g.fill(0, 0, screenWidth, screenHeight, BACKGROUND_COLOR);
        
        // Рисуем заголовок
        g.drawString("AI Assistant", PADDING, PADDING, TEXT_COLOR);
        
        // Рисуем область чата
        int chatY = PADDING + 30;
        g.fill(PADDING, chatY, screenWidth - PADDING, chatY + CHAT_HEIGHT, CHAT_BACKGROUND_COLOR);
        
        // Рисуем историю чата
        int messageY = chatY + PADDING;
        for (ChatMessage message : chatHistory) {
            String text = message.sender + ": " + message.content;
            int color = message.isAi ? AI_TEXT_COLOR : USER_TEXT_COLOR;
            g.drawString(text, PADDING + 5, messageY, color);
            messageY += 20;
            
            // Если сообщение слишком длинное, переносим на новую строку
            if (text.length() > 60) {
                messageY += 15;
            }
        }
        
        // Рисуем область ввода
        int inputY = chatY + CHAT_HEIGHT + PADDING;
        g.fill(PADDING, inputY, screenWidth - PADDING, inputY + INPUT_HEIGHT, INPUT_BACKGROUND_COLOR);
        
        // Рисуем текст ввода
        String displayText = currentInput + (isTyping ? "|" : "");
        g.drawString(displayText, PADDING + 5, inputY + 10, TEXT_COLOR);
        
        // Рисуем кнопку отправки
        int sendButtonX = screenWidth - 80;
        int sendButtonY = inputY + 5;
        g.fill(sendButtonX, sendButtonY, sendButtonX + 60, sendButtonY + 30, 0xFF4CAF50);
        g.drawString("Send", sendButtonX + 15, sendButtonY + 8, TEXT_COLOR);
    }
    
    @Override
    public void onEvent(KernelEvent event) {
        switch (event) {
            case KernelEvent.KeyPressed keyEvent -> handleKeyPress(keyEvent);
            case KernelEvent.MouseClicked mouseEvent -> handleMouseClick((int)mouseEvent.mouseX, (int)mouseEvent.mouseY);
            default -> {} // Игнорируем другие события
        }
    }
    
    private void handleKeyPress(KernelEvent.KeyPressed keyEvent) {
        if (keyEvent.keyCode == 256) { // ESC - закрыть приложение
            onClose();
            return;
        }
        
        if (keyEvent.keyCode == 257) { // Enter - отправить сообщение
            sendMessage();
            return;
        }
        
        if (keyEvent.keyCode == 259) { // Backspace
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
            }
            return;
        }
        
        // Добавляем символ к вводу (только для печатаемых символов)
        if (keyEvent.keyCode >= 32 && keyEvent.keyCode <= 126) { // Печатаемые символы
            currentInput += (char) keyEvent.keyCode;
        }
    }
    
    private void handleMouseClick(int mouseX, int mouseY) {
        int[] screenSize = api.getScreenSize();
        int screenWidth = screenSize[0];
        
        // Проверяем клик по кнопке отправки
        int inputY = PADDING + 30 + CHAT_HEIGHT + PADDING;
        int sendButtonX = screenWidth - 80;
        int sendButtonY = inputY + 5;
        
        if (mouseX >= sendButtonX && mouseX <= sendButtonX + 60 &&
            mouseY >= sendButtonY && mouseY <= sendButtonY + 30) {
            sendMessage();
        }
    }
    
    private void sendMessage() {
        if (currentInput.trim().isEmpty() || currentAiResponse != null) {
            return; // Не отправляем пустые сообщения или если уже ждем ответ
        }
        
        String userMessage = currentInput.trim();
        chatHistory.add(new ChatMessage("You", userMessage, false));
        currentInput = "";
        
        // Отправляем запрос к ИИ
        currentAiResponse = api.askAI(userMessage);
        currentAiResponse.whenComplete((response, throwable) -> {
            if (throwable != null) {
                chatHistory.add(new ChatMessage("AI Assistant", "Извините, произошла ошибка: " + throwable.getMessage(), true));
            } else {
                chatHistory.add(new ChatMessage("AI Assistant", response, true));
            }
            currentAiResponse = null;
        });
    }
    
    @Override
    public void onClose() {
        LOGGER.info("AI Assistant app closing");
        if (currentAiResponse != null) {
            currentAiResponse.cancel(true);
        }
    }
    
    /**
     * Представляет сообщение в чате.
     */
    private static class ChatMessage {
        final String sender;
        final String content;
        final boolean isAi;
        
        ChatMessage(String sender, String content, boolean isAi) {
            this.sender = sender;
            this.content = content;
            this.isAi = isAi;
        }
    }
}
