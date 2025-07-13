package com.loracore.network;

import com.loracore.LoraCoreMod;
import com.loracore.api.dto.OpenAiApiDto.GeneratedQuestInfo;
import com.loracore.api.dto.OpenAiApiDto.Message;
import com.loracore.component.*;
import com.loracore.quest.Quest;
import com.loracore.service.AiService;
import com.loracore.service.GiftService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;

import java.util.List;
import java.util.UUID;

import static com.loracore.service.GiftService.GiftTier.LOVED;

public class ModNetworking {

    // ... методы registerC2SPackets, registerPacketHandlers и часть registerDialogueAndQuestHandlers без изменений ...
    public static void registerC2SPackets() {
        // Регистрация типов пакетов
        PayloadTypeRegistry.playC2S().register(RequestVillagerDataC2SPacket.ID, RequestVillagerDataC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendDialogueMessageC2SPacket.ID, SendDialogueMessageC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetVillagerFrozenC2SPacket.ID, SetVillagerFrozenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AskAiC2SPacket.ID, AskAiC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AcceptQuestC2SPacket.ID, AcceptQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteQuestC2SPacket.ID, CompleteQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(GiveGiftC2SPacket.ID, GiveGiftC2SPacket.CODEC);

        // Регистрация обработчиков
        registerPacketHandlers();
        registerDialogueAndQuestHandlers();
    }
    private static void registerPacketHandlers() {
        // Обработчик запроса данных о жителе
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
                    playerDialogue.initializeDialogue(villager.getUuid(), "");
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                    return;
                }

                villagerComponent.setHasGeneratedData(true);
                villagerComponent.setVillagerName("Думает...");
                ModComponents.VILLAGER_DATA.sync(villager);

                AiService.generateVillagerPersonality(villager, langCode).whenCompleteAsync((info, error) -> {
                    if (error != null) {
                        LoraCoreMod.LOGGER.error("Не удалось сгенерировать личность жителя:", error);
                        villagerComponent.setVillagerName("Загадочный житель");
                        villagerComponent.setPersonality("Этот житель неразговорчив и предпочитает молчать.");
                        player.sendMessage(Text.translatable("error.loracore.dialogue.generic").formatted(Formatting.RED), true);
                    } else {
                        villagerComponent.setVillagerName(info.name());
                        villagerComponent.setPersonality(info.personality());
                    }
                    playerDialogue.initializeDialogue(villager.getUuid(), "");
                    ModComponents.VILLAGER_DATA.sync(villager);
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                }, server);
            });
        });

        // Обработчик "заморозки" жителя
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
    }
    private static void registerDialogueAndQuestHandlers() {
        // Обработчик сообщений в диалоге
        ServerPlayNetworking.registerGlobalReceiver(SendDialogueMessageC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(payload.villagerUuid());
                if (!(entity instanceof VillagerEntity villager)) return;

                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("user", payload.message()));

                List<Message> history = playerDialogue.getDialogueHistory(payload.villagerUuid());

                AiService.continueConversation(history, payload.languageCode(), villager, player).whenCompleteAsync((response, error) -> {
                    if (error != null) {
                        LoraCoreMod.LOGGER.error("Не удалось сгенерировать ответ в диалоге:", error);
                        playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("assistant", "Произошла какая-то ошибка..."));
                        ModComponents.PLAYER_DIALOGUE.sync(player);
                        return;
                    }

                    playerDialogue.addMessageToHistory(payload.villagerUuid(), new Message("assistant", response.dialogue()));

                    if (response.quest() != null) {
                        VillagerDataComponent villagerComponent = ModComponents.VILLAGER_DATA.get(villager);
                        if (!villagerComponent.hasQuestForPlayer(player.getUuid())) {
                            GeneratedQuestInfo questInfo = response.quest();
                            Item goalItem = Registries.ITEM.get(new Identifier(questInfo.goalItem()));
                            Item rewardItem = Registries.ITEM.get(new Identifier(questInfo.rewardItem()));

                            Quest newQuest = new Quest(
                                    UUID.randomUUID(),
                                    villager.getUuid(),
                                    questInfo.title(),
                                    questInfo.description(),
                                    new Quest.FetchGoal(goalItem, questInfo.goalAmount()),
                                    new Quest.QuestReward(rewardItem, questInfo.rewardAmount())
                            );
                            villagerComponent.assignQuestToPlayer(player.getUuid(), newQuest);
                            ModComponents.VILLAGER_DATA.sync(villager);
                        }
                    }
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                }, server);
            });
        });

        // Обработчик принятия квеста
        ServerPlayNetworking.registerGlobalReceiver(AcceptQuestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            UUID villagerUuid = payload.villagerUuid();
            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(villagerUuid);
                if (!(entity instanceof VillagerEntity villager)) return;

                VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(villager);
                Quest questToAccept = villagerData.getAssignedQuest(player.getUuid());

                if (questToAccept == null) {
                    LoraCoreMod.LOGGER.warn("AcceptQuestC2SPacket: No pending quest offer found for player {} by villager {}.", player.getName().getString(), villagerUuid);
                    return;
                }

                PlayerQuestComponent playerQuests = ModComponents.PLAYER_QUEST.get(player);
                playerQuests.addQuest(questToAccept);

                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                String confirmationMsg = String.format("Отлично! Как только принесешь мне %s (%d шт.), дай мне знать.",
                        questToAccept.goal().item().getName().getString(), questToAccept.goal().requiredAmount());
                playerDialogue.addMessageToHistory(villagerUuid, new Message("assistant", confirmationMsg));

                ModComponents.PLAYER_QUEST.sync(player);
                ModComponents.VILLAGER_DATA.sync(villager);
                ModComponents.PLAYER_DIALOGUE.sync(player);
                LoraCoreMod.LOGGER.info("AcceptQuestC2SPacket: Quest '{}' accepted by player {}.", questToAccept.title(), player.getName().getString());
            });
        });

        // Обработчик завершения квеста
        ServerPlayNetworking.registerGlobalReceiver(CompleteQuestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            UUID villagerUuid = payload.villagerUuid();
            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(villagerUuid);
                if (!(entity instanceof VillagerEntity villager)) {
                    LoraCoreMod.LOGGER.warn("CompleteQuestC2SPacket: Villager entity not found for UUID: {}", villagerUuid);
                    return;
                }

                PlayerQuestComponent playerQuests = ModComponents.PLAYER_QUEST.get(player);
                Quest activeQuest = playerQuests.getQuests().stream()
                        .filter(q -> q.villagerGiverUuid().equals(villagerUuid))
                        .findFirst()
                        .orElse(null);

                if (activeQuest == null) return;

                Quest.FetchGoal goal = activeQuest.goal();
                if (player.getInventory().count(goal.item()) >= goal.requiredAmount()) {
                    player.getInventory().remove(itemStack -> itemStack.isOf(goal.item()), goal.requiredAmount(), player.getInventory());
                    player.giveItemStack(new ItemStack(activeQuest.reward().item(), activeQuest.reward().amount()));

                    VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(villager);
                    villagerData.completeQuestForPlayer(player.getUuid());
                    villagerData.addFriendship(player.getUuid(), 10);
                    playerQuests.getQuests().removeIf(q -> q.questId().equals(activeQuest.questId()));

                    PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                    playerDialogue.addMessageToHistory(villagerUuid, new Message("assistant", "Спасибо большое! Вот твоя награда."));

                    ModComponents.PLAYER_QUEST.sync(player);
                    ModComponents.VILLAGER_DATA.sync(villager);
                    ModComponents.PLAYER_DIALOGUE.sync(player);
                    LoraCoreMod.LOGGER.info("CompleteQuestC2SPacket: Quest '{}' completed by player {}.", activeQuest.title(), player.getName().getString());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AskAiC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                PlayerAskHistoryComponent historyComponent = ModComponents.PLAYER_ASK_HISTORY.get(player);
                historyComponent.addMessage(new Message("user", payload.question()));
                ModComponents.PLAYER_ASK_HISTORY.sync(player);

                // ИЗМЕНЕНИЕ: Передаем всю историю в AiService
                AiService.getAnswer(player, historyComponent.getHistory(), payload.languageCode())
                        .whenCompleteAsync((answer, error) -> {
                            if (error != null) {
                                LoraCoreMod.LOGGER.error("AI service failed to provide an answer", error);
                                historyComponent.addMessage(new Message("assistant", "Произошла ошибка при обращении к AI."));
                            } else {
                                // ИЗМЕНЕНИЕ: Добавляем проверку на null
                                if (answer == null || answer.isBlank()) {
                                    historyComponent.addMessage(new Message("assistant", "ИИ вернул пустой или некорректный ответ."));
                                } else {
                                    historyComponent.addMessage(new Message("assistant", answer));
                                }
                            }
                            ModComponents.PLAYER_ASK_HISTORY.sync(player);
                        }, server);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GiveGiftC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                Entity entity = player.getServerWorld().getEntity(payload.villagerUuid());
                if (!(entity instanceof VillagerEntity villager)) return;

                ItemStack giftStack = player.getMainHandStack();
                if (giftStack.isEmpty()) return;

                VillagerProfession profession = villager.getVillagerData().getProfession();
                GiftService.GiftResult result = GiftService.evaluateGift(profession, giftStack.getItem());

                VillagerDataComponent villagerData = ModComponents.VILLAGER_DATA.get(villager);
                villagerData.addFriendship(player.getUuid(), result.friendshipChange());

                String feedbackKey = switch (result.tier()) {
                    case LOVED -> "gui.loracore.dialogue.gift.loved";
                    case LIKED -> "gui.loracore.dialogue.gift.liked";
                    case NEUTRAL -> "gui.loracore.dialogue.gift.neutral";
                    case DISLIKED -> "gui.loracore.dialogue.gift.disliked";
                };

                // Отправляем системное сообщение в чат диалога
                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                // Используем Text.translatable, чтобы клиент сам перевел текст
                playerDialogue.addMessageToHistory(villager.getUuid(), new Message("system", feedbackKey));

                // Забираем подарок
                giftStack.decrement(1);

                // Синхронизируем
                ModComponents.VILLAGER_DATA.sync(villager);
                ModComponents.PLAYER_DIALOGUE.sync(player);
            });
        });
    }
}