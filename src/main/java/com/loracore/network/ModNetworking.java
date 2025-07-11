package com.loracore.network;

import com.loracore.LoraCoreMod;
import com.loracore.api.dto.OpenAiApiDto.GeneratedQuestInfo;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

public class ModNetworking {

    public static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(RequestVillagerDataC2SPacket.ID, RequestVillagerDataC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendDialogueMessageC2SPacket.ID, SendDialogueMessageC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetVillagerFrozenC2SPacket.ID, SetVillagerFrozenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AskAiC2SPacket.ID, AskAiC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AcceptQuestC2SPacket.ID, AcceptQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteQuestC2SPacket.ID, CompleteQuestC2SPacket.CODEC);

        registerPacketHandlers();
    }

    private static void registerPacketHandlers() {
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
                    // Системный промпт будет формироваться динамически в AiService,
                    // поэтому здесь просто инициализируем диалог, если его еще нет.
                    playerDialogue.initializeDialogue(villager.getUuid(), ""); // Промпт будет добавлен в AiService
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

        registerDialogueAndQuestHandlers();
    }

    private static void registerDialogueAndQuestHandlers() {
        // ОБРАБОТЧИК СООБЩЕНИЙ В ДИАЛОГЕ
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

        // ОБРАБОТЧИК ПРИНЯТИЯ КВЕСТА
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

        // ОБРАБОТЧИК ЗАВЕРШЕНИЯ КВЕСТА
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

        // ПРОЧИЕ ОБРАБОТЧИКИ
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
    }
}