package com.loracore.network;

import com.loracore.AiMod;
import com.loracore.api.dto.OpenAiApiDto.Message;
import com.loracore.component.ModComponents;
import com.loracore.component.PlayerDialogueComponent;
import com.loracore.component.PlayerQuestComponent;
import com.loracore.component.VillagerDataComponent;
import com.loracore.quest.Quest;
import com.loracore.service.AiService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModNetworking {

    public static void registerC2SPackets() {
        // Регистрация всех пакетов
        PayloadTypeRegistry.playC2S().register(RequestVillagerDataC2SPacket.ID, RequestVillagerDataC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendDialogueMessageC2SPacket.ID, SendDialogueMessageC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetVillagerFrozenC2SPacket.ID, SetVillagerFrozenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AskAiC2SPacket.ID, AskAiC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AcceptQuestC2SPacket.ID, AcceptQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteQuestC2SPacket.ID, CompleteQuestC2SPacket.CODEC);

        registerPacketHandlers();
    }

    private static void registerPacketHandlers() {
        // Обработчик для первоначального знакомства с жителем
        ServerPlayNetworking.registerGlobalReceiver(RequestVillagerDataC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                ServerWorld world = player.getServerWorld();
                UUID villagerUuid = payload.villagerUuid();
                String langCode = payload.languageCode();
                Entity entity = world.getEntity(villagerUuid);

                if (!(entity instanceof VillagerEntity villager)) return;

                VillagerDataComponent villagerComponent = ModComponents.VILLAGER_DATA.get(villager);
                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);

                if (villagerComponent.hasGeneratedData()) {
                    String systemPrompt = "Ты - " + villagerComponent.getVillagerName() + ". " + villagerComponent.getPersonality();
                    // ИЗМЕНЕНИЕ: Убираем конкретные детали из промпта AI для предложения квеста.
                    // AI будет знать, что может предложить квест, но не будет сам генерировать его условия.
                    if (!villagerComponent.hasQuestForPlayer(player.getUuid())) {
                        systemPrompt += " Ты можешь предложить игроку простой квест, если он попросит или если это уместно. Если решишь предложить, закончи свой ответ специальным тегом [QUEST_OFFER]. Не придумывай детали квеста (предметы, количество) сам, а просто предложи квест.";
                    }
                    systemPrompt += " Говори с игроком от этого лица.";
                    playerDialogue.initializeDialogue(villager.getUuid(), systemPrompt);
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                    return;
                }

                villagerComponent.setHasGeneratedData(true);
                villagerComponent.setVillagerName("Думает...");
                ModComponents.VILLAGER_DATA.sync(villager);

                AiService.generateVillagerPersonality(villager, langCode).whenCompleteAsync((info, error) -> {
                    if (error != null) {
                        AiMod.LOGGER.error("Не удалось сгенерировать личность жителя:", error);
                        villagerComponent.setHasGeneratedData(false);
                        villagerComponent.setVillagerName("");
                    } else {
                        villagerComponent.setVillagerName(info.name());
                        villagerComponent.setPersonality(info.personality());

                        // ИЗМЕНЕНИЕ: Создаем новый промпт здесь, чтобы он был final
                        String finalSystemPrompt = "Ты - " + info.name() + ". " + info.personality();
                        if (!villagerComponent.hasQuestForPlayer(player.getUuid())) {
                            finalSystemPrompt += " Ты можешь предложить игроку простой квест, если он попросит или если это уместно. Если решишь предложить, закончи свой ответ специальным тегом [QUEST_OFFER]. Не придумывай детали квеста (предметы, количество) сам, а просто предложи квест.";
                        }
                        finalSystemPrompt += " Говори с игроком от этого лица.";

                        playerDialogue.initializeDialogue(villager.getUuid(), finalSystemPrompt);
                        ModComponents.PLAYER_DIALOGUE.sync(player);
                    }
                    ModComponents.VILLAGER_DATA.sync(villager);
                }, server);
            });
        });

        // Остальные обработчики ...
        registerOtherPacketHandlers();
    }

    private static void registerOtherPacketHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(SendDialogueMessageC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(payload.villagerUuid());
                if (!(entity instanceof VillagerEntity villager)) return;

                VillagerDataComponent villagerComponent = ModComponents.VILLAGER_DATA.get(villager);
                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);

                playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("user", payload.message()));

                List<Message> history = new ArrayList<>(playerDialogue.getDialogueHistory(payload.villagerUuid()));
                // ИЗМЕНЕНИЕ: Обновляем системный промпт для каждого запроса к AI
                // Убеждаемся, что инструкция для квеста всегда присутствует и актуальна
                // (если квест не назначен, она должна быть, если назначен - убираем)
                String questInstruction = " Ты можешь предложить игроку простой квест, если он попросит или если это уместно. Если решишь предложить, закончи свой ответ специальным тегом [QUEST_OFFER]. Не придумывай детали квеста (предметы, количество) сам, а просто предложи квест.";
                if (!history.isEmpty() && "system".equals(history.get(0).role())) {
                    Message oldPrompt = history.get(0);
                    String newContent = oldPrompt.content();
                    if (!villagerComponent.hasQuestForPlayer(player.getUuid()) && !newContent.contains(questInstruction)) {
                        newContent += questInstruction;
                    } else if (villagerComponent.hasQuestForPlayer(player.getUuid()) && newContent.contains(questInstruction)) {
                        // Если квест уже есть, убираем инструкцию по предложению нового квеста
                        newContent = newContent.replace(questInstruction, "");
                    }
                    history.set(0, new Message(oldPrompt.role(), newContent));
                }

                AiService.continueConversation(history, payload.languageCode()).whenCompleteAsync((response, error) -> {
                    String aiResponseContent;
                    if (error != null) {
                        AiMod.LOGGER.error("Не удалось сгенерировать ответ в диалоге:", error);
                        aiResponseContent = "Произошла какая-то ошибка...";
                    } else {
                        aiResponseContent = response;
                    }

                    // Check if AI offered a quest AND player does not already have a quest from this villager
                    if (aiResponseContent.contains("[QUEST_OFFER]") && !villagerComponent.hasQuestForPlayer(player.getUuid())) {
                        String baseAiResponse = aiResponseContent.replace("[QUEST_OFFER]", "").trim();

                        AiService.generateQuest(villager, player, payload.languageCode()).whenCompleteAsync((generatedQuest, questError) -> {
                            if (questError != null) {
                                AiMod.LOGGER.error("Не удалось сгенерировать квест после предложения AI:", questError);
                                playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("assistant", baseAiResponse + " (Квест временно недоступен из-за ошибки генерации.)"));
                            } else {
                                // Quest successfully generated, assign it to the villager's component as the pending offer
                                villagerComponent.assignQuestToPlayer(player.getUuid(), generatedQuest);

                                // Append actual quest details to the AI's response
                                String questDetailsFormatted = String.format(" У меня есть для тебя небольшая задачка! Нужно помочь мне добыть %s (%d шт.). Как тебе такое предложение? Готов взяться?",
                                        generatedQuest.goal().item().getName().getString(), generatedQuest.goal().requiredAmount());
                                playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("assistant", baseAiResponse + questDetailsFormatted + " [QUEST_OFFER]"));
                            }
                            // Always sync after adding to history
                            ModComponents.PLAYER_DIALOGUE.sync(player);
                            ModComponents.VILLAGER_DATA.sync(villager); // Синхронизируем, чтобы клиент обновил состояние квеста
                        }, server);
                    } else {
                        // Normal dialogue response, or quest offer rejected because player already has a quest
                        playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("assistant", aiResponseContent));
                        ModComponents.PLAYER_DIALOGUE.sync(player);
                    }
                }, server);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AskAiC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.execute(() -> AiService.getAnswer(player, payload.question(), payload.languageCode()));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SetVillagerFrozenC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(payload.villagerUuid());
                if (entity instanceof VillagerEntity villager) {
                    villager.setCustomer(payload.frozen() ? player : null);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AcceptQuestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            String langCode = payload.languageCode();
            UUID villagerUuid = payload.villagerUuid();

            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(villagerUuid);
                if (!(entity instanceof VillagerEntity villager)) return;

                VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(villager);
                PlayerQuestComponent playerQuests = ModComponents.PLAYER_QUEST.get(player);
                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);

                // Квест уже должен быть "предложен" и храниться в villagerData
                Quest questToAccept = villagerData.getAssignedQuest(player.getUuid()); // "assigned" здесь означает "pending offer"

                if (questToAccept == null) {
                    AiMod.LOGGER.warn("AcceptQuestC2SPacket: No pending quest offer found for player {} by villager {}. Cannot accept.", player.getName().getString(), villagerUuid);
                    return;
                }

                // Добавляем квест в список активных квестов игрока
                playerQuests.addQuest(questToAccept);
                // Важно: Квест остаётся в villagerData.assignedQuests до тех пор, пока не будет завершён/отклонён.
                // Это позволяет нам в getActiveQuestForThisVillager() на клиенте получать этот квест.

                String confirmationMsg = String.format("Отлично! Как только принесешь мне %s (%d шт.), дай мне знать.",
                        questToAccept.goal().item().getName().getString(), questToAccept.goal().requiredAmount());
                playerDialogue.addMessageToHistory(villagerUuid, new Message("assistant", confirmationMsg));

                ModComponents.PLAYER_QUEST.sync(player);
                ModComponents.VILLAGER_DATA.sync(villager); // Синхронизируем, чтобы состояние квеста обновилось на клиенте
                ModComponents.PLAYER_DIALOGUE.sync(player);
                AiMod.LOGGER.info("AcceptQuestC2SPacket: Quest '{}' (ID: {}) accepted by player {}.", questToAccept.title(), questToAccept.questId(), player.getName().getString());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CompleteQuestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            UUID villagerUuid = payload.villagerUuid();
            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(villagerUuid);
                if (!(entity instanceof VillagerEntity villager)) {
                    AiMod.LOGGER.warn("CompleteQuestC2SPacket: Villager entity not found or not a villager for UUID: {}", villagerUuid);
                    return;
                }

                VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(villager);
                Quest quest = villagerData.getAssignedQuest(player.getUuid());

                if (quest == null) {
                    AiMod.LOGGER.info("CompleteQuestC2SPacket: No quest assigned to player {} by villager {}.", player.getName().getString(), villagerData.getVillagerName());
                    return;
                }

                Quest.FetchGoal goal = quest.goal();
                boolean hasEnoughItems = player.getInventory().count(goal.item()) >= goal.requiredAmount();

                AiMod.LOGGER.info("CompleteQuestC2SPacket: Checking quest for player {}. Quest: '{}', Goal Item: {}, Required: {}, Has: {}. Can complete: {}. Villager: {}",
                        player.getName().getString(), quest.title(), goal.item().getName().getString(), goal.requiredAmount(), player.getInventory().count(goal.item()), hasEnoughItems, villagerData.getVillagerName());

                if (hasEnoughItems) {
                    player.getInventory().remove(itemStack -> itemStack.isOf(goal.item()), goal.requiredAmount(), player.getInventory());
                    player.giveItemStack(new ItemStack(quest.reward().item(), quest.reward().amount()));

                    villagerData.completeQuestForPlayer(player.getUuid()); // Удаляем квест из компонента жителя
                    PlayerQuestComponent playerQuests = ModComponents.PLAYER_QUEST.get(player);
                    playerQuests.getQuests().removeIf(q -> q.questId().equals(quest.questId())); // Удаляем из списка квестов игрока

                    PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                    playerDialogue.addMessageToHistory(villagerUuid, new Message("assistant", "Спасибо большое! Вот твоя награда."));

                    ModComponents.PLAYER_QUEST.sync(player);
                    ModComponents.VILLAGER_DATA.sync(villager);
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                    AiMod.LOGGER.info("CompleteQuestC2SPacket: Quest '{}' completed successfully for player {}.", quest.title(), player.getName().getString());
                } else {
                    AiMod.LOGGER.info("CompleteQuestC2SPacket: Player {} does not have enough items to complete quest '{}'.", player.getName().getString(), quest.title());
                }
            });
        });
    }
}