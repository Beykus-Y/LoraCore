package com.aiassist.service;

import com.aiassist.AiMod;
import com.aiassist.ModConfig;
import com.aiassist.component.ModComponents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import com.aiassist.service.RecipeService; // ИЗМЕНЕНИЕ: импортируем RecipeService
import com.aiassist.api.OpenAiApiClient;
import com.aiassist.api.dto.OpenAiApiDto.*;
import com.aiassist.util.PlayerContextProvider;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AiService {

    private static final OpenAiApiClient apiClient = new OpenAiApiClient();
    private static final Gson GSON = new Gson();
    private static List<String> availableModels = new ArrayList<>();
    // ИЗМЕНЕНИЕ: Ключевые слова для определения вопроса о рецепте
    private static final List<String> CRAFTING_KEYWORDS = List.of("скрафтить", "сделать", "рецепт", "крафт");


    public static List<String> getAvailableModels() {
        return Collections.unmodifiableList(availableModels);
    }

    public static CompletableFuture<List<String>> fetchModels() {
        // ... код без изменений ...
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("API ключ не настроен."));
        }

        return apiClient.fetchModelsAsync(apiUrl, apiKey)
                .thenApply(modelsResponse -> {
                    if (modelsResponse == null || modelsResponse.data() == null) {
                        return List.of();
                    }
                    List<String> modelIds = modelsResponse.data().stream()
                            .map(ModelData::id)
                            .sorted()
                            .collect(Collectors.toList());
                    availableModels = new ArrayList<>(modelIds); // Обновляем кеш
                    return modelIds;
                });
    }

    public static void getAnswer(ServerPlayerEntity player, String question) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            player.sendMessage(Text.literal("§cОшибка: API ключ не настроен."));
            return;
        }

        // --- ИЗМЕНЕНИЕ: ЛОГИКА ОБОГАЩЕНИЯ ПРОМПТА ---
        String lowerCaseQuestion = question.toLowerCase();
        boolean isCraftingQuestion = CRAFTING_KEYWORDS.stream().anyMatch(lowerCaseQuestion::contains);

        String systemPrompt = PlayerContextProvider.getContextFor(player);

        if (isCraftingQuestion) {
            Optional<String> recipeInfo = RecipeService.findRecipeFromQuery(question, player);
            if (recipeInfo.isPresent()) {
                systemPrompt += "\nВАЖНО: Игрок спросил о рецепте. Вот точные данные из игры:\n---\n" +
                        recipeInfo.get() +
                        "\n---\nТвой ответ ДОЛЖЕН основываться на этих данных. Объясни этот рецепт игроку простыми словами. Не придумывай рецепт сам.";
                AiMod.LOGGER.info("Найден рецепт для запроса, обогащаем промпт.");
            }
        }
        // --- КОНЕЦ ИЗМЕНЕНИЯ ---

        List<Message> messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", question)
        );
        ChatRequest chatRequest = new ChatRequest(modelId, messages, null);

        apiClient.createChatCompletionAsync(apiUrl, apiKey, chatRequest).whenCompleteAsync((response, error) -> {
            if (error != null) {
                AiMod.LOGGER.error("Ошибка запроса к API: ", error);
                player.sendMessage(Text.literal("§cНе удалось связаться с ИИ."));
                return;
            }

            if (response.choices() == null || response.choices().isEmpty()) {
                player.sendMessage(Text.literal("§cИИ вернул пустой ответ."));
                return;
            }

            String answer = response.choices().get(0).message().content();
            player.sendMessage(Text.literal("§aИИ отвечает: §f" + answer));
        }, player.getServer());
    }

    // ... остальная часть файла (generateStructureInfo и другие методы) без изменений ...
    public static CompletableFuture<GeneratedStructureInfo> generateStructureInfo(String structureType, String biomeId) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("API ключ не настроен."));
        }

        String prompt = createStructurePrompt(structureType, biomeId);
        List<Message> messages = List.of(new Message("system", "Отвечай только в JSON формате."), new Message("user", prompt));
        ChatRequest requestPayload = new ChatRequest(modelId, messages, 100);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, requestPayload)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        throw new RuntimeException("AI returned an empty or invalid choice list.");
                    }
                    String content = response.choices().get(0).message().content();
                    return parseGeneratedInfo(content);
                });
    }
    /**
     * Генерирует уникальную личность для жителя на основе его профессии и биома.
     */
    public static CompletableFuture<GeneratedVillagerInfo> generateVillagerPersonality(VillagerEntity villager) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("API ключ не настроен."));
        }

        String profession = Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()).toString();
        String biome = villager.getWorld().getBiome(villager.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("unknown_biome");

        String prompt = """
        Ты - сценарист для игры Minecraft. Создай короткую личность для жителя деревни.
        Профессия: %s. Биом: %s.
        Придумай ему простое, запоминающееся имя (1-2 слова) и краткое описание характера/личности (1 предложение).
        Отвечай ТОЛЬКО в формате JSON, без лишних слов:
        { "name": "Имя Жителя", "personality": "Описание его личности." }
        """.formatted(profession, biome);

        List<Message> messages = List.of(new Message("system", "Ты отвечаешь только в формате JSON."), new Message("user", prompt));
        ChatRequest requestPayload = new ChatRequest(modelId, messages, 150);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, requestPayload)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        throw new RuntimeException("AI вернул пустой ответ при генерации личности.");
                    }
                    String content = response.choices().getFirst().message().content();
                    return GSON.fromJson(content, GeneratedVillagerInfo.class);
                });
    }

    /**
     * Продолжает диалог с ИИ, используя предыдущую историю.
     */
    public static CompletableFuture<String> continueConversation(List<Message> history, String newPlayerMessage) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        // Создаем новый список, чтобы не изменять оригинальный
        List<Message> messages = new ArrayList<>(history);
        messages.add(new Message("user", newPlayerMessage));

        ChatRequest requestPayload = new ChatRequest(modelId, messages, 200);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, requestPayload)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        return "Произошла ошибка, ИИ не ответил.";
                    }
                    return response.choices().getFirst().message().content();
                });
    }

    private static String createStructurePrompt(String structureType, String biomeId) {
        String friendlyStructure = structureType.replace("minecraft:", "").replace("_", " ");
        String friendlyBiome = biomeId.replace("minecraft:", "").replace("_", " ");
        return """
        Сгенерируй уникальное, короткое (1-3 слова) название и очень краткое (1 предложение) описание для структуры типа '%s' в биоме '%s'.
        Название должно быть в стиле фэнтези/Minecraft.
        Ответ должен быть ТОЛЬКО в JSON формате: { "name": "Название", "description": "Описание." }
        """.formatted(friendlyStructure, friendlyBiome);
    }

    private static GeneratedStructureInfo parseGeneratedInfo(String jsonContent) {
        // Удаляем возможные markdown-блоки кода
        String cleanJson = jsonContent.replace("```json", "").replace("```", "").trim();
        try {
            GeneratedStructureInfo info = GSON.fromJson(cleanJson, GeneratedStructureInfo.class);
            if (info == null || info.name() == null || info.name().trim().isEmpty()) {
                throw new JsonSyntaxException("Parsed info or name is null/empty.");
            }
            return info;
        } catch (JsonSyntaxException e) {
            AiMod.LOGGER.error("Не удалось распарсить JSON ответа AI: {}", cleanJson, e);
            // Возвращаем объект-заглушку в случае ошибки
            return new GeneratedStructureInfo("Неизвестная структура", "Не удалось сгенерировать описание.");
        }
    }
}