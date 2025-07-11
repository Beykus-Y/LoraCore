package com.loracore.api.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Контейнер для всех объектов передачи данных (DTO), используемых для взаимодействия с OpenAI API.
 */
public final class OpenAiApiDto {
    // Приватный конструктор, чтобы предотвратить создание экземпляров этого класса-контейнера.
    private OpenAiApiDto() {}


    // DTO для запросов
    public record Message(String role, String content) {}

    // НОВОЕ: Вложенный record для указания JSON-режима
    public record ResponseFormat(String type) {}

    // ИЗМЕНЕНО: Добавлено поле response_format
    public record ChatRequest(String model, List<Message> messages, Integer max_tokens, @SerializedName("response_format") ResponseFormat responseFormat) {
        // Добавляем конструктор для обратной совместимости, где response_format не используется
        public ChatRequest(String model, List<Message> messages, Integer max_tokens) {
            this(model, messages, max_tokens, null);
        }
    }

    // DTO для ответов
    public record Choice(Message message) {}
    public record OpenAiResponse(List<Choice> choices) {}

    // DTO для моделей
    public record ModelData(String id) {}
    public record ModelsListResponse(List<ModelData> data) {}

    // DTO для генерации
    public record GeneratedStructureInfo(String name, String description) {}
    public record GeneratedVillagerInfo(String name, String personality) {}
    public record GeneratedQuestInfo(String title, String description, String goalItem, int goalAmount, String rewardItem, int rewardAmount) {}
    public record GeneratedDialogueResponse(String dialogue, GeneratedQuestInfo quest) {}
}
