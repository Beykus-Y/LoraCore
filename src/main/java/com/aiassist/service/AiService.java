package com.aiassist.service;

import com.aiassist.AiMod;
import com.aiassist.ModConfig;
import com.aiassist.api.OpenAiApiClient;
import com.aiassist.api.dto.OpenAiApiDto.*;
import com.aiassist.component.ModComponents;
import com.aiassist.component.VillagerDataComponent;
import com.aiassist.quest.Quest;
import com.aiassist.util.PlayerContextProvider;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AiService {

    private static final OpenAiApiClient apiClient = new OpenAiApiClient();
    private static final Gson GSON = new Gson();
    private static List<String> availableModels = new ArrayList<>();
    private static final List<String> CRAFTING_KEYWORDS = List.of("скрафтить", "сделать", "рецепт", "крафт", "craft", "make", "recipe");

    private static String getLanguageInstruction(String langCode) {
        String languageName = switch (langCode) {
            case "ru_ru" -> "Современный русский";
            case "rpr" -> "Дореформенный русский (используй букву ѣ, букву і, и твердый знакъ на конце словъ после согласныхъ)";
            default -> "English (US)";
        };
        return "Важно: Твой ответ должен быть написан исключительно на следующем языке: " + languageName + ".";
    }

    public static List<String> getAvailableModels() {
        return Collections.unmodifiableList(availableModels);
    }

    public static CompletableFuture<List<String>> fetchModels() {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("API key is not configured"));
        }
        return apiClient.fetchModelsAsync(apiUrl, apiKey).thenApply(modelsResponse -> {
            if (modelsResponse == null || modelsResponse.data() == null) return List.of();
            List<String> modelIds = modelsResponse.data().stream().map(ModelData::id).sorted().collect(Collectors.toList());
            availableModels = new ArrayList<>(modelIds);
            return modelIds;
        });
    }

    public static void getAnswer(ServerPlayerEntity player, String question, String languageCode) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        if (apiKey.trim().isEmpty()) {
            player.sendMessage(Text.translatable("error.aiassist.api_key_missing").formatted(Formatting.RED));
            return;
        }

        String lowerCaseQuestion = question.toLowerCase();
        boolean isCraftingQuestion = CRAFTING_KEYWORDS.stream().anyMatch(lowerCaseQuestion::contains);
        String systemPrompt = PlayerContextProvider.getContextFor(player) + "\n" + getLanguageInstruction(languageCode);

        if (isCraftingQuestion) {
            Optional<String> recipeInfo = RecipeService.findRecipeFromQuery(question, player);
            if (recipeInfo.isPresent()) {
                systemPrompt += "\nВАЖНО: Игрок спросил о рецепте. Вот точные данные из игры:\n---\n" +
                        recipeInfo.get() +
                        "\n---\nТвой ответ ДОЛЖЕН основываться на этих данных. Объясни этот рецепт игроку простыми словами.";
            }
        }

        List<Message> messages = List.of(new Message("system", systemPrompt), new Message("user", question));
        ChatRequest chatRequest = new ChatRequest(modelId, messages, 250);

        apiClient.createChatCompletionAsync(apiUrl, apiKey, chatRequest).whenCompleteAsync((response, error) -> {
            if (error != null) {
                player.sendMessage(Text.translatable("error.aiassist.api_connection_failed").formatted(Formatting.RED));
                return;
            }
            if (response.choices() == null || response.choices().isEmpty()) {
                player.sendMessage(Text.translatable("error.aiassist.api_empty_response").formatted(Formatting.RED));
                return;
            }
            String answer = response.choices().get(0).message().content();
            player.sendMessage(Text.translatable("chat.aiassist.ai.response_prefix", Text.literal(answer).formatted(Formatting.WHITE)).formatted(Formatting.GREEN));
        }, player.getServer());
    }

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
        Reply ONLY in JSON format: { "name": "Villager's Name", "personality": "Description of their personality." }
        """.formatted(langInstruction, profession, biome);

        List<Message> messages = List.of(new Message("system", "You only reply in JSON format."), new Message("user", prompt));
        ChatRequest payload = new ChatRequest(modelId, messages, 150);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> GSON.fromJson(response.choices().getFirst().message().content(), GeneratedVillagerInfo.class));
    }

    // ИСПРАВЛЕНИЕ 1: ВОЗВРАЩЕН ОТСУТСТВУЮЩИЙ МЕТОД
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
                    return parseGeneratedInfo(response.choices().get(0).message().content());
                });
    }

    public static CompletableFuture<String> continueConversation(List<Message> history, String languageCode) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        List<Message> messages = new ArrayList<>(history);
        String languageInstruction = getLanguageInstruction(languageCode);

        if (!messages.isEmpty() && "system".equals(messages.get(0).role())) {
            Message oldPrompt = messages.get(0);
            messages.set(0, new Message(oldPrompt.role(), oldPrompt.content() + "\n" + languageInstruction));
        } else {
            messages.add(0, new Message("system", languageInstruction));
        }

        ChatRequest payload = new ChatRequest(modelId, messages, 200);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> {
                    if (response.choices() == null || response.choices().isEmpty()) {
                        throw new RuntimeException("AI returned an empty or invalid choice list.");
                    }
                    return response.choices().getFirst().message().content();
                });
    }

    // ИСПРАВЛЕНИЕ 2: ПРОМПТ ПЕРЕВЕДЕН НА АНГЛИЙСКИЙ ДЛЯ ЯЗЫКОВО-НЕЙТРАЛЬНОЙ ГЕНЕРАЦИИ
    private static String createStructurePrompt(String structureType, String biomeId) {
        String friendlyStructure = structureType.replace("minecraft:", "").replace("_", " ");
        String friendlyBiome = biomeId.replace("minecraft:", "").replace("_", " ");
        return """
        Generate a unique, short (1-3 words) name and a very brief (1 sentence) description for a structure of type '%s' in a '%s' biome.
        The name should be in a fantasy/Minecraft style.
        Reply ONLY in JSON format: { "name": "The Name", "description": "A description." }
        """.formatted(friendlyStructure, friendlyBiome);
    }

    private static GeneratedStructureInfo parseGeneratedInfo(String jsonContent) {
        String cleanJson = jsonContent.replace("```json", "").replace("```", "").trim();
        try {
            GeneratedStructureInfo info = GSON.fromJson(cleanJson, GeneratedStructureInfo.class);
            if (info == null || info.name() == null || info.name().trim().isEmpty()) {
                throw new JsonSyntaxException("Parsed info or name is null/empty.");
            }
            return info;
        } catch (JsonSyntaxException e) {
            AiMod.LOGGER.error("Не удалось распарсить JSON ответа AI: {}", cleanJson, e);
            return new GeneratedStructureInfo("structure.aiassist.unknown.name", "structure.aiassist.unknown.description");
        }
    }

    public static CompletableFuture<Quest> generateQuest(VillagerEntity giver, ServerPlayerEntity player, String languageCode) {
        String apiKey = ModConfig.instance.API_KEY;
        String apiUrl = ModConfig.instance.API_URL;
        String modelId = ModConfig.instance.MODEL_ID;

        VillagerDataComponent data = ModComponents.VILLAGER_DATA.get(giver);
        String villagerContext = "Личность жителя: " + data.getVillagerName() + ", " + data.getPersonality() + ".";
        String playerContext = PlayerContextProvider.getContextFor(player);
        String langInstruction = getLanguageInstruction(languageCode);

        String prompt = """
                %s
                %s
                %s
                
                Ты - геймдизайнер в Minecraft. Создай квест для игрока от лица жителя.
                Квест должен быть простым, в стиле "принеси-подай".
                Название и описание должны соответствовать личности жителя.
                Целевой предмет (goalItem) и предмет-награда (rewardItem) должны быть простыми, существующими в игре предметами (например, "minecraft:stone", "minecraft:apple").
                Количество должно быть разумным (goalAmount 1-16, rewardAmount 1-8).
                
                Отвечай ТОЛЬКО в формате JSON:
                {
                  "title": "Название квеста",
                  "description": "Описание квеста от лица жителя",
                  "goalItem": "minecraft:item_id",
                  "goalAmount": 10,
                  "rewardItem": "minecraft:item_id",
                  "rewardAmount": 2
                }
                """.formatted(langInstruction, villagerContext, playerContext);

        List<Message> messages = List.of(new Message("system", "You only reply in JSON format."), new Message("user", prompt));
        ChatRequest payload = new ChatRequest(modelId, messages, 200);

        return apiClient.createChatCompletionAsync(apiUrl, apiKey, payload)
                .thenApply(response -> {
                    GeneratedQuestInfo info = GSON.fromJson(response.choices().getFirst().message().content(), GeneratedQuestInfo.class);

                    Item goalItem = Registries.ITEM.get(new Identifier(info.goalItem()));
                    Item rewardItem = Registries.ITEM.get(new Identifier(info.rewardItem()));

                    // Проверка, что предметы существуют
                    if (goalItem == Items.AIR || rewardItem == Items.AIR) {
                        throw new RuntimeException("AI generated a quest with a non-existent item.");
                    }

                    return new Quest(
                            UUID.randomUUID(),
                            giver.getUuid(),
                            info.title(),
                            info.description(),
                            new Quest.FetchGoal(goalItem, info.goalAmount()),
                            new Quest.QuestReward(rewardItem, info.rewardAmount())
                    );
                });
    }
}
