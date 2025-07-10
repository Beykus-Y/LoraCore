package com.loracore.api.dto;

import java.util.List;

/**
 * Контейнер для всех объектов передачи данных (DTO), используемых для взаимодействия с OpenAI API.
 */
public final class OpenAiApiDto {
    // Приватный конструктор, чтобы предотвратить создание экземпляров этого класса-контейнера.
    private OpenAiApiDto() {}

    // --- DTO для /chat/completions ---
    public record Message(String role, String content) {}
    public record ChatRequest(String model, List<Message> messages, Integer max_tokens) {}
    public record Choice(Message message) {}
    public record OpenAiResponse(List<Choice> choices) {}

    // --- DTO для /models ---
    public record ModelData(String id) {}
    public record ModelsListResponse(List<ModelData> data) {}

    // --- DTO для генерации информации о структуре ---
    public record GeneratedStructureInfo(String name, String description) {}


    public record GeneratedVillagerInfo(String name, String personality) {}
    public record GeneratedQuestInfo(String title, String description, String goalItem, int goalAmount, String rewardItem, int rewardAmount) {}
}
