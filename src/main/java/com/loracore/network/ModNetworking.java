// [ИСПРАВЛЕНО]
package com.loracore.network;

import com.loracore.LoraCoreMod;
import com.loracore.api.dto.OpenAiApiDto.GeneratedQuestInfo;
import com.loracore.api.dto.OpenAiApiDto.Message;
import com.loracore.component.*;
import com.loracore.component.data.*;
// ИСПРАВЛЕНО: Полностью переработан обработчик VFS
import com.loracore.computer.VirtualFileSystemManager;
import com.loracore.network.vfs.VfsRequestC2SPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
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

import java.util.*;

public class ModNetworking {

    public static void registerC2SPackets() {
        // Регистрация типов пакетов (без изменений)
        PayloadTypeRegistry.playC2S().register(RequestTabletDataC2SPacket.ID, RequestTabletDataC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(BootTabletS2CPacket.ID, BootTabletS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestVillagerDataC2SPacket.ID, RequestVillagerDataC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendDialogueMessageC2SPacket.ID, SendDialogueMessageC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetVillagerFrozenC2SPacket.ID, SetVillagerFrozenC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AskAiC2SPacket.ID, AskAiC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(AcceptQuestC2SPacket.ID, AcceptQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CompleteQuestC2SPacket.ID, CompleteQuestC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(GiveGiftC2SPacket.ID, GiveGiftC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(VfsRequestC2SPacket.ID, VfsRequestC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(VfsResponseS2CPacket.ID, VfsResponseS2CPacket.CODEC);

        // Регистрация обработчиков
        registerTabletHandlers();
        registerDialogueAndQuestHandlers();
        registerVfsHandlers(); // ИСПРАВЛЕНО: Этот метод теперь содержит правильную логику
    }

    /**
     * Обработчики, связанные с VFS.
     */
    private static void registerVfsHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(VfsRequestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            // Вся работа выполняется в основном потоке сервера
            server.execute(() -> {
                LoraCoreMod.LOGGER.info("[VFS] Processing VFS request on server thread: Operation={}, Path={}", payload.operation(), payload.path());

                // ИСПРАВЛЕНО: Единственное правильное действие - вызвать синглтон-менеджер
                VirtualFileSystemManager.VFSResponse response = VirtualFileSystemManager.getInstance().performOperation(
                        payload.fsUuid(),
                        payload.operation(),
                        payload.path(),
                        payload.content()
                );
                LoraCoreMod.LOGGER.info("[VFS] Operation result: [{}]. Sending response to client for callbackId: {}", response.type(), payload.callbackId());
                // Отправляем результат обратно клиенту
                ServerPlayNetworking.send(player, new VfsResponseS2CPacket(payload.callbackId(), response.type(), response.data()));
            });
        });
    }

    // Остальные методы (registerTabletHandlers, registerDialogueAndQuestHandlers) остаются без изменений
    // ... (скопируйте их из вашего текущего файла, они были корректны)
    /**
     * [НОВЫЙ МЕТОД] Обработчики, связанные с планшетом.
     */
    private static void registerTabletHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestTabletDataC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                ItemStack stack = player.getMainHandStack();
                if (!(stack.getItem() instanceof com.loracore.item.TabletItem)) return;

                MotherboardData mobo = stack.get(ModComponents.MOTHERBOARD_DATA);
                if (mobo == null) {
                    player.sendMessage(Text.literal("Device is critically damaged. Motherboard not found.").formatted(Formatting.RED), true);
                    return;
                }

                // =========================================================================
                //                         НОВАЯ КЛЮЧЕВАЯ ЛОГИКА
                // =========================================================================

                // ШАГ 1: Найти жесткий диск внутри планшета (берем первый).
                Optional<ItemStack> storageStackOpt = mobo.storage().stream().findFirst();
                if (storageStackOpt.isEmpty()) {
                    player.sendMessage(Text.literal("No storage device found.").formatted(Formatting.RED), true);
                    return;
                }

                // ШАГ 2: Получаем наш новый компонент с картой UUID с самого планшета.
                FileSystemsData fsData = stack.get(ModComponents.FILE_SYSTEMS_DATA);
                if (fsData == null) {
                    // Такого быть не должно, если мы правильно добавили компонент в ModItems, но это защита.
                    fsData = new FileSystemsData(new java.util.HashMap<>());
                }
                final UUID fsUuid;
                // ШАГ 3: Проверяем, есть ли у диска в слоте 0 уже присвоенный UUID.
                if (!fsData.uuids().containsKey("0")) {
                    LoraCoreMod.LOGGER.info("[VFS] Tablet unformatted. Generating new VFS UUID and saving it to ItemStack NBT...");

                    fsUuid = UUID.randomUUID();
                    LoraCoreMod.LOGGER.info("[VFS UUID] Generated NEW UUID for tablet: {}", fsUuid);

                    // [ИЗМЕНЕНО] Тип карты теперь Map<String, UUID>
                    Map<String, UUID> newUuids = new java.util.HashMap<>(fsData.uuids());
                    // [ИЗМЕНЕНО] Кладем ключ "0" как СТРОКУ
                    newUuids.put("0", fsUuid);

                    FileSystemsData newFsData = new FileSystemsData(newUuids);

                    stack.set(ModComponents.FILE_SYSTEMS_DATA, newFsData);
                    player.getInventory().setStack(player.getInventory().selectedSlot, stack.copy());
                    player.getInventory().markDirty();

                } else {
                    // [ИЗМЕНЕНО] Получаем ключ "0" как СТРОКУ
                    fsUuid = fsData.uuids().get("0");
                    LoraCoreMod.LOGGER.info("[VFS UUID] Using EXISTING UUID for tablet: {}", fsUuid);
                }

                // ШАГ 4: Теперь, когда у нас гарантированно есть UUID, продолжаем загрузку как обычно.
                Optional<CpuData> cpuDataOpt = mobo.cpu().map(s -> s.get(ModComponents.CPU_DATA));
                Optional<FirmwareData> firmwareDataOpt = mobo.firmware().map(s -> s.get(ModComponents.FIRMWARE_DATA));
                int totalRamKb = mobo.ram().stream().mapToInt(s -> s.getOrDefault(ModComponents.RAM_DATA, new RamData(0)).sizeKb()).sum();

                if (cpuDataOpt.isEmpty() || firmwareDataOpt.isEmpty()) {
                    player.sendMessage(Text.literal("Device is bricked. Missing CPU or Firmware.").formatted(Formatting.RED), true);
                    return;
                }

                String bootScriptPath = firmwareDataOpt.get().recoveryScript().toString();

                ServerPlayNetworking.send(player, new BootTabletS2CPacket(fsUuid, bootScriptPath, cpuDataOpt.get().architecture(), totalRamKb));
            });
        });
    }

    private static void registerDialogueAndQuestHandlers() {
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

                villagerData.completeQuestForPlayer(player.getUuid());

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

                AiService.getAnswer(player, historyComponent.getHistory(), payload.languageCode())
                        .whenCompleteAsync((answer, error) -> {
                            if (error != null) {
                                LoraCoreMod.LOGGER.error("AI service failed to provide an answer", error);
                                historyComponent.addMessage(new Message("assistant", "Произошла ошибка при обращении к AI."));
                            } else {
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

                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                playerDialogue.addMessageToHistory(villager.getUuid(), new Message("system", feedbackKey));

                giftStack.decrement(1);

                ModComponents.VILLAGER_DATA.sync(villager);
                ModComponents.PLAYER_DIALOGUE.sync(player);
            });
        });
    }
}