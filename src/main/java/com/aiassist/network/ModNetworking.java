package com.aiassist.network;

import com.aiassist.AiMod;
import com.aiassist.api.dto.OpenAiApiDto.Message;
import com.aiassist.component.ModComponents;
import com.aiassist.component.PlayerDialogueComponent;
import com.aiassist.component.VillagerDataComponent;
import com.aiassist.service.AiService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.UUID;

public class ModNetworking {

    public static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(RequestVillagerDataC2SPacket.ID, RequestVillagerDataC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SendDialogueMessageC2SPacket.ID, SendDialogueMessageC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SetVillagerFrozenC2SPacket.ID, SetVillagerFrozenC2SPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestVillagerDataC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServerWorld();
            UUID villagerUuid = payload.villagerUuid();

            player.getServer().execute(() -> {
                Entity entity = world.getEntity(villagerUuid);
                if (entity instanceof VillagerEntity villager) {
                    VillagerDataComponent villagerComponent = ModComponents.VILLAGER_DATA.get(villager);
                    if (villagerComponent.hasGeneratedData()) {
                        // Если данные жителя уже есть, просто инициализируем диалог для игрока
                        PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                        String systemPrompt = "Ты - " + villagerComponent.getVillagerName() + ". " + villagerComponent.getPersonality() + ". Говори с игроком от этого лица.";
                        playerDialogue.initializeDialogue(villager.getUuid(), systemPrompt);
                        ModComponents.PLAYER_DIALOGUE.sync(player);
                        return;
                    }

                    villagerComponent.setHasGeneratedData(true);
                    villagerComponent.setVillagerName("Думает...");
                    ModComponents.VILLAGER_DATA.sync(villager);

                    AiService.generateVillagerPersonality(villager).whenCompleteAsync((info, error) -> {
                        if (error != null) {
                            AiMod.LOGGER.error("Не удалось сгенерировать личность жителя:", error);
                            villagerComponent.setHasGeneratedData(false);
                            villagerComponent.setVillagerName("");
                        } else {
                            villagerComponent.setVillagerName(info.name());
                            villagerComponent.setPersonality(info.personality());

                            PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                            String systemPrompt = "Ты - " + info.name() + ". " + info.personality() + ". Говори с игроком от этого лица.";
                            playerDialogue.initializeDialogue(villager.getUuid(), systemPrompt);
                            ModComponents.PLAYER_DIALOGUE.sync(player);
                        }
                        ModComponents.VILLAGER_DATA.sync(villager);
                    }, player.getServer());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SendDialogueMessageC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID villagerUuid = payload.villagerUuid();
            String message = payload.message();

            player.getServer().execute(() -> {
                PlayerDialogueComponent playerDialogue = ModComponents.PLAYER_DIALOGUE.get(player);
                playerDialogue.addMessageToHistory(villagerUuid, new Message("user", message));

                List<Message> history = playerDialogue.getDialogueHistory(villagerUuid);

                AiService.continueConversation(history, message)
                        .whenCompleteAsync((response, error) -> {
                            String aiResponse = (error != null) ? "Произошла ошибка..." : response;
                            playerDialogue.addMessageToHistory(villagerUuid, new Message("assistant", aiResponse));
                            ModComponents.PLAYER_DIALOGUE.sync(player);
                        }, player.getServer());
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SetVillagerFrozenC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServerWorld();
            UUID villagerUuid = payload.villagerUuid();
            boolean frozen = payload.frozen();

            player.getServer().execute(() -> {
                Entity entity = world.getEntity(villagerUuid);
                if (entity instanceof VillagerEntity villager) {
                    // Назначаем или убираем "клиента", что заставляет жителя стоять и смотреть
                    villager.setCustomer(frozen ? player : null);
                }
            });
        });
    }
}