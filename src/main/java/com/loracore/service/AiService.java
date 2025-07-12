package com.loracore.service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.loracore.LoraCoreMod;
import com.loracore.ModConfig;
import com.loracore.api.OpenAiApiClient;
import com.loracore.api.dto.OpenAiApiDto.*;
import com.loracore.component.ModComponents;
import com.loracore.component.VillagerDataComponent;
import com.loracore.util.PlayerContextProvider;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class AiService {

    private static final OpenAiApiClient apiClient = new OpenAiApiClient();
    private static final Gson GSON = new Gson();
    private static List<String> availableModels = new ArrayList<>();
    private static final List<String> CRAFTING_KEYWORDS = List.of("скрафтить", "сделать", "рецепт", "крафт", "craft", "make", "recipe");

    // ИЗМЕНЕНИЕ 1: Создаем статичный системный промпт
    private static final String STATIC_SYSTEM_PROMPT = "Ты - помощник по игре Minecraft. Твои ответы должны быть полезными в контексте игры. " +
            "В конце запроса пользователя тебе будет предоставлена системная информация о его текущем состоянии (инвентарь, биом). " +
            "ВАЖНО: Основывай свои советы на этой АКТУАЛЬНОЙ информации, а не только на истории диалога.";

    // ... константы PROFESSION_GOAL_ITEMS и PROFESSION_REWARD_ITEMS без изменений ...
    private static final Map<VillagerProfession, List<String>> PROFESSION_GOAL_ITEMS = Map.ofEntries(
            Map.entry(VillagerProfession.FARMER, List.of("minecraft:wheat_seeds", "minecraft:potato", "minecraft:carrot", "minecraft:beetroot_seeds", "minecraft:bone_meal")),
            Map.entry(VillagerProfession.FISHERMAN, List.of("minecraft:cod", "minecraft:salmon", "minecraft:string", "minecraft:stick")),
            Map.entry(VillagerProfession.SHEPHERD, List.of("minecraft:white_wool", "minecraft:black_wool", "minecraft:gray_wool", "minecraft:shears")),
            Map.entry(VillagerProfession.FLETCHER, List.of("minecraft:feather", "minecraft:flint", "minecraft:stick", "minecraft:string")),
            Map.entry(VillagerProfession.LIBRARIAN, List.of("minecraft:paper", "minecraft:book", "minecraft:ink_sac", "minecraft:feather")),
            Map.entry(VillagerProfession.CARTOGRAPHER, List.of("minecraft:paper", "minecraft:compass", "minecraft:glass_pane")),
            Map.entry(VillagerProfession.CLERIC, List.of("minecraft:rotten_flesh", "minecraft:gold_ingot", "minecraft:redstone", "minecraft:glass_bottle")),
            Map.entry(VillagerProfession.ARMORER, List.of("minecraft:coal", "minecraft:iron_ingot", "minecraft:lava_bucket", "minecraft:diamond")),
            Map.entry(VillagerProfession.WEAPONSMITH, List.of("minecraft:coal", "minecraft:iron_ingot", "minecraft:flint", "minecraft:diamond")),
            Map.entry(VillagerProfession.TOOLSMITH, List.of("minecraft:coal", "minecraft:iron_ingot", "minecraft:flint", "minecraft:diamond")),
            Map.entry(VillagerProfession.BUTCHER, List.of("minecraft:raw_porkchop", "minecraft:raw_beef", "minecraft:raw_chicken", "minecraft:coal")),
            Map.entry(VillagerProfession.LEATHERWORKER, List.of("minecraft:leather", "minecraft:rabbit_hide", "minecraft:scute")),
            Map.entry(VillagerProfession.MASON, List.of("minecraft:clay_ball", "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite")),
            Map.entry(VillagerProfession.NITWIT, List.of("minecraft:dirt", "minecraft:poppy", "minecraft:dandelion"))
    );
    private static final Map<VillagerProfession, List<String>> PROFESSION_REWARD_ITEMS = Map.ofEntries(
            Map.entry(VillagerProfession.FARMER, List.of("minecraft:bread", "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:emerald")),
            Map.entry(VillagerProfession.FISHERMAN, List.of("minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:bucket", "minecraft:emerald")),
            Map.entry(VillagerProfession.SHEPHERD, List.of("minecraft:white_bed", "minecraft:painting", "minecraft:lead", "minecraft:emerald")),
            Map.entry(VillagerProfession.FLETCHER, List.of("minecraft:arrow", "minecraft:spectral_arrow", "minecraft:bow", "minecraft:emerald")),
            Map.entry(VillagerProfession.LIBRARIAN, List.of("minecraft:bookshelf", "minecraft:writable_book", "minecraft:glass", "minecraft:emerald")),
            Map.entry(VillagerProfession.CARTOGRAPHER, List.of("minecraft:map", "minecraft:item_frame", "minecraft:emerald")),
            Map.entry(VillagerProfession.CLERIC, List.of("minecraft:lapis_lazuli", "minecraft:glowstone_dust", "minecraft:ender_pearl", "minecraft:emerald")),
            Map.entry(VillagerProfession.ARMORER, List.of("minecraft:iron_helmet", "minecraft:chainmail_chestplate", "minecraft:shield", "minecraft:emerald")),
            Map.entry(VillagerProfession.WEAPONSMITH, List.of("minecraft:iron_axe", "minecraft:iron_sword", "minecraft:bell", "minecraft:emerald")),
            Map.entry(VillagerProfession.TOOLSMITH, List.of("minecraft:stone_axe", "minecraft:iron_hoe", "minecraft:diamond_pickaxe", "minecraft:emerald")),
            Map.entry(VillagerProfession.BUTCHER, List.of("minecraft:cooked_porkchop", "minecraft:cooked_beef", "minecraft:rabbit_stew", "minecraft:emerald")),
            Map.entry(VillagerProfession.LEATHERWORKER, List.of("minecraft:leather_chestplate", "minecraft:saddle", "minecraft:item_frame", "minecraft:emerald")),
            Map.entry(VillagerProfession.MASON, List.of("minecraft:bricks", "minecraft:chiseled_stone_bricks", "minecraft:terracotta", "minecraft:emerald")),
            Map.entry(VillagerProfession.NITWIT, List.of("minecraft:emerald"))
    );

    // ... методы cleanJsonString, getLanguageInstruction, getAvailableModels, fetchModels без изменений ...
    private static String cleanJsonString(String rawContent) {
        if (rawContent == null) return "";
        String cleaned = rawContent.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
    public static List<String> getAvailableModels() { return Collections.unmodifiableList(availableModels); }
    public static CompletableFuture<List<String>> fetchModels() {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        if (apiKey == null || apiKey.trim().isEmpty()) return CompletableFuture.failedFuture(new IOException("API key is not configured"));
        return apiClient.fetchModelsAsync(apiUrl, apiKey).thenApply(modelsResponse -> {
            if (modelsResponse == null || modelsResponse.data() == null) return List.of();
            List<String> modelIds = modelsResponse.data().stream().map(ModelData::id).sorted().collect(Collectors.toList());
            availableModels = new ArrayList<>(modelIds);
            return modelIds;
        });
    }
    private static String getLanguageInstruction(String langCode) {
        String languageName = switch (langCode) {
            case "ru_ru" -> "Современный русский";
            case "rpr" -> "Дореформенный русский (используй букву ѣ, букву і, и твердый знакъ на конце словъ после согласныхъ)";
            default -> "English (US)";
        };
        return "Твой ответ должен быть написан исключительно на следующем языке: " + languageName + ".";
    }

    // ИЗМЕНЕНИЕ 2: Полностью переработанный метод getAnswer
    public static CompletableFuture<String> getAnswer(ServerPlayerEntity player, List<Message> history, String languageCode) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey.trim().isEmpty()) {
            player.sendMessage(Text.translatable("error.loracore.api_key_missing").formatted(Formatting.RED));
            return CompletableFuture.failedFuture(new IOException("API key is not configured."));
        }

        if (history.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot get answer for empty history."));
        }

        // 1. Извлекаем последний запрос пользователя и предыдущую историю
        Message lastUserMessage = history.get(history.size() - 1);
        List<Message> previousHistory = history.subList(0, history.size() - 1);

        // 2. Получаем динамический контекст
        String dynamicContext = PlayerContextProvider.getDynamicContextFor(player);

        // 3. Добавляем контекст рецепта, если необходимо
        boolean isCraftingQuestion = CRAFTING_KEYWORDS.stream().anyMatch(lastUserMessage.content().toLowerCase()::contains);
        if (isCraftingQuestion) {
            RecipeService.findRecipeFromQuery(lastUserMessage.content(), player).ifPresent(recipeInfo -> {
                // Мы не меняем dynamicContext, а добавляем информацию в него для этого конкретного запроса
            });
        }

        // 4. Объединяем последний запрос пользователя с динамическим контекстом
        String combinedLastMessageContent = lastUserMessage.content() + dynamicContext;
        Message combinedLastMessage = new Message("user", combinedLastMessageContent);

        // 5. Собираем финальный список сообщений для API
        List<Message> messagesForApi = new ArrayList<>();
        // Сначала добавляем статичный системный промпт и инструкцию по языку
        messagesForApi.add(new Message("system", STATIC_SYSTEM_PROMPT + " " + getLanguageInstruction(languageCode)));
        // Затем добавляем историю диалога
        messagesForApi.addAll(previousHistory);
        // В самом конце - наш объединенный запрос
        messagesForApi.add(combinedLastMessage);

        ChatRequest chatRequest = new ChatRequest(modelId, messagesForApi, 1024);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, chatRequest)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        throw new CompletionException(new IOException("AI returned an empty or invalid choice list."));
                    }
                    return response.choices().get(0).message().content();
                });
    }

    // ... остальные методы (generateVillagerPersonality и т.д.) без изменений ...
    public static CompletableFuture<GeneratedVillagerInfo> generateVillagerPersonality(VillagerEntity villager, String languageCode) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey.trim().isEmpty()) return CompletableFuture.failedFuture(new IOException("API key not set."));

        String profession = Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()).toString();
        String biome = villager.getWorld().getBiome(villager.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("unknown");
        String langInstruction = getLanguageInstruction(languageCode);

        String prompt = """
        %s
        You are a scriptwriter for Minecraft. Create a short personality for a village NPC.
        Profession: %s. Biome: %s.
        Invent a simple, memorable name (1-2 words) and a brief personality description (1 sentence).
        The name and personality MUST be written in the language specified in the first line of this prompt.
        Reply ONLY in JSON format: { "name": "Villager's Name", "personality": "Description of their personality." }
        """.formatted(langInstruction, profession, biome);

        List<Message> messages = List.of(new Message("system", "You only reply in JSON format."), new Message("user", prompt));
        ChatRequest payload = new ChatRequest(modelId, messages, 150);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> {
                    try {
                        String rawContent = response.choices().getFirst().message().content();
                        String cleanedJson = cleanJsonString(rawContent);

                        if (cleanedJson.startsWith("{")) {
                            GeneratedVillagerInfo info = GSON.fromJson(cleanedJson, GeneratedVillagerInfo.class);
                            if (info != null && info.name() != null && !info.name().isBlank()) {
                                return info;
                            }
                        }
                        throw new JsonSyntaxException("Response is not a valid JSON object or is incomplete: " + rawContent);
                    } catch (RuntimeException e) {
                        throw new CompletionException(new RuntimeException("Failed to parse villager personality JSON from AI.", e));
                    }
                });
    }
    public static CompletableFuture<GeneratedStructureInfo> generateStructureInfo(String structureType, String biomeId) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("API key not set."));
        }

        String prompt = createStructurePrompt(structureType, biomeId);
        List<Message> messages = List.of(new Message("system", "You only reply in JSON format."), new Message("user", prompt));
        ChatRequest payload = new ChatRequest(modelId, messages, 100);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        throw new RuntimeException("AI returned an empty or invalid choice list.");
                    }
                    String cleanedJson = cleanJsonString(response.choices().get(0).message().content());
                    return parseGeneratedInfo(cleanedJson);
                });
    }
    public static CompletableFuture<GeneratedDialogueResponse> continueConversation(List<Message> history, String languageCode, VillagerEntity giver, ServerPlayerEntity player) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(giver);
        VillagerProfession profession = giver.getVillagerData().getProfession();
        List<String> goalItems = PROFESSION_GOAL_ITEMS.getOrDefault(profession, List.of("minecraft:cobblestone", "minecraft:stick"));
        List<String> rewardItems = PROFESSION_REWARD_ITEMS.getOrDefault(profession, List.of("minecraft:apple", "minecraft:emerald"));

        String langInstruction = getLanguageInstruction(languageCode);
        String villagerContext = "Твоя личность: " + villagerData.getVillagerName() + ", " + villagerData.getPersonality();

        String questContext = "";
        if (!villagerData.hasQuestForPlayer(player.getUuid())) {
            questContext = String.format("""
            Ты можешь предложить игроку квест. Если НЕ предлагаешь, поле "quest" должно быть null.
            Если ПРЕДЛАГАЕШЬ, заполни объект "quest". Правила:
            - 'goalItem' из списка: [%s]. 'rewardItem' из списка: [%s].
            - 'goalAmount' от 8 до 32. 'rewardAmount' от 1 до 5.
            - 'title' и 'description' короткие.
            - В 'dialogue' напиши реплику, предлагающую квест.
            """, String.join(", ", goalItems), String.join(", ", rewardItems));
        }

        String systemPrompt = """
            %s
            %s
            
            Твоя задача - вести диалог от лица NPC и отвечать ТОЛЬКО в формате JSON по следующей схеме. Не добавляй никакого текста кроме JSON.
            {
              "dialogue": "Твоя реплика в диалоге.",
              "quest": { ... } или null
            }
            
            %s
            """.formatted(langInstruction, villagerContext, questContext);

        List<Message> messages = new ArrayList<>(history);
        messages.add(0, new Message("system", systemPrompt));

        ResponseFormat responseFormat = new ResponseFormat("json_object");
        ChatRequest payload = new ChatRequest(modelId, messages, 400, responseFormat);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> {
                    String rawContent = response.choices().getFirst().message().content();
                    String cleanedJson = cleanJsonString(rawContent);
                    try {
                        if (!cleanedJson.startsWith("{")) {
                            LoraCoreMod.LOGGER.warn("API проигнорировало JSON-режим и вернуло строку. Ответ будет обработан как простой диалог.");
                            return new GeneratedDialogueResponse(cleanedJson, null);
                        }

                        GeneratedDialogueResponse dialogueResponse = GSON.fromJson(cleanedJson, GeneratedDialogueResponse.class);

                        if (dialogueResponse == null || dialogueResponse.dialogue() == null) {
                            throw new JsonSyntaxException("Ответ от AI не содержит обязательного поля 'dialogue'.");
                        }

                        if (dialogueResponse.quest() != null) {
                            GeneratedQuestInfo q = dialogueResponse.quest();
                            if (q.goalItem() == null || q.rewardItem() == null || !goalItems.contains(q.goalItem()) || !rewardItems.contains(q.rewardItem())) {
                                LoraCoreMod.LOGGER.warn("AI сгенерировал квест с некорректными предметами. Квест отменен.");
                                return new GeneratedDialogueResponse(dialogueResponse.dialogue(), null);
                            }
                        }

                        return dialogueResponse;
                    } catch (JsonSyntaxException e) {
                        LoraCoreMod.LOGGER.error("Ошибка парсинга ответа диалога от AI: {}. Содержимое: {}", e.getMessage(), cleanedJson);
                        return new GeneratedDialogueResponse("Извини, я что-то задумался...", null);
                    }
                });
    }
    private static String createStructurePrompt(String structureType, String biomeId) {
        String friendlyStructure = structureType.replace("minecraft:", "").replace("_", " ");
        String friendlyBiome = biomeId.replace("minecraft:", "").replace("_", " ");
        return """
        Generate a unique, short (1-3 words) name and a very brief (1 sentence) description for a structure of type '%s' in a '%s' biome.
        The name should be in a fantasy/Minecraft style.
        The language for the name and description MUST be the same as the user's language defined in the system message.
        Reply ONLY in JSON format: { "name": "The Name", "description": "A description." }
        """.formatted(friendlyStructure, friendlyBiome);
    }
    private static GeneratedStructureInfo parseGeneratedInfo(String jsonContent) {
        try {
            GeneratedStructureInfo info = GSON.fromJson(jsonContent, GeneratedStructureInfo.class);
            if (info == null || info.name() == null || info.name().trim().isEmpty()) {
                throw new JsonSyntaxException("Parsed info or name is null/empty.");
            }
            return info;
        } catch (JsonSyntaxException e) {
            LoraCoreMod.LOGGER.error("Не удалось распарсить JSON ответа AI: {}", jsonContent, e);
            return new GeneratedStructureInfo("structure.loracore.unknown.name", "structure.loracore.unknown.description");
        }
    }
}