// [ИСПРАВЛЕНО]
package com.loracore.network;

import com.loracore.LoraCoreMod;
import com.loracore.api.dto.OpenAiApiDto.GeneratedQuestInfo;
import com.loracore.api.dto.OpenAiApiDto.Message;
import com.loracore.component.*;
import com.loracore.component.data.*;
// ИСПРАВЛЕНО: Полностью переработан обработчик VFS
import com.loracore.computer.*;
import com.loracore.computer.device.IDevice;
import com.loracore.item.TabletItem;
import com.loracore.network.graphics.GpuCommand;
import com.loracore.network.graphics.GpuCommandC2SPacket;
import com.loracore.network.graphics.ScreenUpdateS2CPacket;
import com.loracore.network.input.CharTypedC2SPacket;
import com.loracore.network.input.KeyPressedC2SPacket;
import com.loracore.network.input.MouseClickedC2SPacket;
import com.loracore.network.vfs.VfsRequestC2SPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import com.loracore.quest.Quest;
import com.loracore.service.AiService;
import com.loracore.service.GiftService;
import com.loracore.service.TabletRenderService;
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


import java.lang.reflect.Method;
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
        PayloadTypeRegistry.playC2S().register(GpuCommandC2SPacket.ID, GpuCommandC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenUpdateS2CPacket.ID, ScreenUpdateS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(RunLuaScriptC2SPacket.ID, RunLuaScriptC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(KeyPressedC2SPacket.ID, KeyPressedC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CharTypedC2SPacket.ID, CharTypedC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(MouseClickedC2SPacket.ID, MouseClickedC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SwitchToClientKernelS2CPacket.ID, SwitchToClientKernelS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(InvokeDeviceMethodC2SPacket.ID, InvokeDeviceMethodC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(DeviceMethodResultS2CPacket.ID, DeviceMethodResultS2CPacket.CODEC);


        // Регистрация обработчиков
        registerTabletHandlers();
        registerDialogueAndQuestHandlers();
        registerVfsHandlers(); // ИСПРАВЛЕНО: Этот метод теперь содержит правильную логику
        registerGpuHandlers();
        registerInputHandlers();
        registerLuaScriptHandlers();
        registerDeviceHandlers();
    }

    /**
     * Обработчики, связанные с VFS.
     */
    private static void registerVfsHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(VfsRequestC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null || payload.fsUuid() == null) return;

            server.execute(() -> {
                try {
                    // Ключевое исправление: Получаем экземпляр ImageVfs, который работает с .img файлом,
                    // а не WorldStorageVFS, который работает с папками.
                    // Емкость здесь не так важна, так как к моменту запроса файлов
                    // ВМ уже должна была быть создана и инициализировала VFS с правильной емкостью.
                    IFileSystem vfs = ImageVfsManager.getInstance().getFor(payload.fsUuid(), 1024);

                    VfsResponseS2CPacket.ResponseType responseType;
                    String responseData;

                    switch (payload.operation()) {
                        case READ_BYTES:
                            // Этот кейс используется для загрузки .jar файлов ядра и приложений
                            byte[] bytes = vfs.readBytes(payload.path());
                            responseData = Base64.getEncoder().encodeToString(bytes);
                            responseType = VfsResponseS2CPacket.ResponseType.STRING;
                            break;

                        case READ:
                            // Для обычных текстовых файлов
                            responseData = vfs.read(payload.path()).tojstring();
                            responseType = responseData != null ? VfsResponseS2CPacket.ResponseType.STRING : VfsResponseS2CPacket.ResponseType.NIL;
                            break;

                        case EXISTS:
                            responseData = "";
                            responseType = vfs.exists(payload.path()) ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE;
                            break;

                        case ISDIR:
                            responseData = "";
                            responseType = vfs.isDirectory(payload.path()) ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE;
                            break;

                        case WRITE:
                            boolean wrote = vfs.write(payload.path(), payload.content());
                            responseData = "";
                            responseType = wrote ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE;
                            break;

                        case MAKEDIR:
                            boolean madeDir = vfs.makeDir(payload.path());
                            responseData = "";
                            responseType = madeDir ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE;
                            break;

                        case DELETE:
                            boolean deleted = vfs.delete(payload.path());
                            responseData = "";
                            responseType = deleted ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE;
                            break;

                        case LIST:
                            responseData = vfs.list(payload.path());
                            responseType = responseData != null ? VfsResponseS2CPacket.ResponseType.TABLE_JSON : VfsResponseS2CPacket.ResponseType.NIL;
                            break;

                        default:
                            responseData = "";
                            responseType = VfsResponseS2CPacket.ResponseType.NIL;
                            break;
                    }

                    // Ваша логика по отправке больших файлов остается актуальной.
                    // Теперь она будет работать, так как vfs.readBytes сможет найти kernel.jar.
                    if (responseData.length() > 25000) { // Безопасный лимит для одного пакета
                        sendLargeFileInChunks(player, payload.fsUuid(), payload.callbackId(), responseData);
                    } else {
                        ServerPlayNetworking.send(player, new VfsResponseS2CPacket(payload.fsUuid(), payload.callbackId(), responseType, responseData));
                    }

                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("VFS Operation failed for fsUUID {} path '{}': {}", payload.fsUuid(), payload.path(), e.getMessage());
                    // Отправляем клиенту ответ о неудаче
                    ServerPlayNetworking.send(player, new VfsResponseS2CPacket(payload.fsUuid(), payload.callbackId(), VfsResponseS2CPacket.ResponseType.NIL, ""));
                }
            });
        });
    }
    
    /**
     * Отправляет большой файл по частям
     */
    private static void sendLargeFileInChunks(ServerPlayerEntity player, UUID fsUuid, int callbackId, String data) {
        final int CHUNK_SIZE = 25000; // Размер одного чанка (безопасно для Minecraft)
        int totalChunks = (data.length() + CHUNK_SIZE - 1) / CHUNK_SIZE; // Округление вверх
        
        LoraCoreMod.LOGGER.info("[VFS] Sending large file: {} chars, {} chunks, chunk size: {}", data.length(), totalChunks, CHUNK_SIZE);
        
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, data.length());
            String chunk = data.substring(start, end);
            
            LoraCoreMod.LOGGER.info("[VFS] Sending chunk {}/{}: {} chars ({} to {})", i + 1, totalChunks, chunk.length(), start, end);
            
            VfsResponseS2CPacket packet = new VfsResponseS2CPacket(
                fsUuid, 
                callbackId, 
                VfsResponseS2CPacket.ResponseType.LARGE_DATA, 
                chunk, 
                i, 
                totalChunks
            );
            
            ServerPlayNetworking.send(player, packet);
            
            // Небольшая задержка между чанками для предотвращения переполнения буфера
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        LoraCoreMod.LOGGER.info("[VFS] Finished sending large file in {} chunks", totalChunks);
    }
    private static void registerGpuHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(GpuCommandC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                // ИСПРАВЛЕНИЕ 3: Используем UUID из пакета для получения нужного экрана
                UUID tabletUuid = payload.tabletUuid();
                ServerScreenState screen = TabletScreenManager.getInstance().getScreen(tabletUuid);

                // Если экран для этого UUID существует, обрабатываем команду
                if (screen != null) {
                    TabletRenderService service = TabletRenderService.getInstance();
                    GpuCommand command = payload.command();

                    switch (command) {
                        case GpuCommand.Fill fill ->
                                service.processFill(screen, fill.x(), fill.y(), fill.width(), fill.height(), fill.color());
                        case GpuCommand.DrawText text ->
                                service.processDrawText(screen, text.x(), text.y(), text.text(), text.color());
                        case GpuCommand.Copy copy ->
                                service.processCopy(screen, copy.x(), copy.y(), copy.width(), copy.height(), copy.toX(), copy.toY());
                    }
                }
            });
        });
    }

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
                if (!(stack.getItem() instanceof TabletItem)) return;

                // 1. Получаем или создаем ВМ
                VirtualMachine vm = VirtualMachineManager.getInstance().getOrCreate(player, stack);

                // ИСПРАВЛЕНИЕ: Даем игроку понятную обратную связь
                if (vm == null) {
                    player.sendMessage(Text.literal("Планшет неисправен: отсутствует накопитель! Попробуйте переложить его в инвентаре.").formatted(Formatting.RED), true);
                    return;
                }

                // 2. Если ВМ выключена, запускаем ее с BIOS
                if (!vm.isOn()) {
                    LoraCoreMod.LOGGER.info("Запуск выключенной ВМ {} по запросу игрока.", vm.getTabletUuid());
                    String biosContent = vm.getResourceLoader().load("os/bios.lua");
                    if (biosContent != null && !biosContent.isEmpty()) {
                        vm.start(biosContent);
                    } else {
                        player.sendMessage(Text.literal("Критическая ошибка: BIOS не найден.").formatted(Formatting.RED), true);
                        return;
                    }
                }

                // 3. Собираем данные для клиента
                UUID tabletUuid = stack.get(ModComponents.TABLET_UUID);
                MotherboardData mobo = stack.get(ModComponents.MOTHERBOARD_DATA);

                // Получаем UUID диска из слота
                UUID fsUuid = mobo.storage().stream()
                        .findFirst()
                        .map(hdd -> hdd.get(ModComponents.FILE_SYSTEMS_DATA))
                        .map(FileSystemsData::fsUuid)
                        .orElse(null); // Будет null, если диска нет

                if (mobo == null || fsUuid == null || tabletUuid == null) {
                    player.sendMessage(Text.literal("Критическая ошибка: компоненты планшета повреждены.").formatted(Formatting.RED), true);
                    return;
                }

                int totalRamKb = mobo.ram().stream()
                        .mapToInt(ramStack -> Optional.ofNullable(ramStack.get(ModComponents.RAM_DATA)).map(RamData::sizeKb).orElse(0))
                        .sum();

                // 4. ВСЕГДА отправляем пакет на открытие экрана
                ServerPlayNetworking.send(player, new BootTabletS2CPacket(fsUuid, tabletUuid, totalRamKb));

                // 5. Проверяем, не нужно ли сразу переключиться на Java-ядро
                if (vm.getCurrentState() == VirtualMachine.State.JAVA_KERNEL) {
                    ServerPlayNetworking.send(player, new SwitchToClientKernelS2CPacket("/boot/kernel.jar"));
                }
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
    private static void registerInputHandlers() {
        // Обработчик для KeyPressed
        ServerPlayNetworking.registerGlobalReceiver(KeyPressedC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // ИСПРАВЛЕНИЕ: Получаем сервер через игрока
            player.getServer().execute(() -> {
                VirtualMachine vm = VirtualMachineManager.getInstance().get(payload.tabletUuid());
                if (vm != null && vm.isRunning()) {
                    vm.pushEvent("key", payload.keyCode());
                }
            });
        });

        // Обработчик для CharTyped
        ServerPlayNetworking.registerGlobalReceiver(CharTypedC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // ИСПРАВЛЕНИЕ: Получаем сервер через игрока
            player.getServer().execute(() -> {
                VirtualMachine vm = VirtualMachineManager.getInstance().get(payload.tabletUuid());
                if (vm != null && vm.isRunning()) {
                    vm.pushEvent("char", String.valueOf(payload.chr()));
                }
            });
        });

        // Обработчик для MouseClicked
        ServerPlayNetworking.registerGlobalReceiver(MouseClickedC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // ИСПРАВЛЕНИЕ: Получаем сервер через игрока
            player.getServer().execute(() -> {
                VirtualMachine vm = VirtualMachineManager.getInstance().get(payload.tabletUuid());
                if (vm != null && vm.isRunning()) {
                    vm.pushEvent("mouse_click", payload.x(), payload.y(), payload.button());
                }
            });
        });
    }
    private static void registerLuaScriptHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(RunLuaScriptC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;

            server.execute(() -> {
                VirtualMachine vm = VirtualMachineManager.getInstance().get(payload.tabletUuid());
                if (vm == null || !vm.isRunning()) return;

                // Загружаем скрипт из VFS и запускаем его в серверной ВМ
                vm.getResourceLoader().load(payload.scriptPath());

                String scriptContent = vm.getResourceLoader().load(payload.scriptPath());
                if (scriptContent != null) {
                    vm.startNewLuaThread(99, scriptContent, null); // Используем временный ID потока
                } else {
                    LoraCoreMod.LOGGER.error("Java-ядро запросило запуск несуществующего Lua-скрипта: {}", payload.scriptPath());
                }
            });
        });
    }
    private static void registerDeviceHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(InvokeDeviceMethodC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            MinecraftServer server = player.getServer();
            server.execute(() -> {
                VirtualMachine vm = VirtualMachineManager.getInstance().get(payload.tabletUuid());
                if (vm == null) return;

                Object deviceObj = vm.getDevices().stream()
                        .filter(d -> d.getClass().getSimpleName().equalsIgnoreCase(payload.deviceType() + "Device"))
                        .findFirst()
                        .orElse(null);

                if (deviceObj == null) {
                    String errorMsg = "Device '" + payload.deviceType() + "' not found in VM.";
                    ServerPlayNetworking.send(player, new DeviceMethodResultS2CPacket(payload.requestId(), false, DeviceMethodResultS2CPacket.resultToJson(errorMsg)));
                    return;
                }

                // =======================================================
                //          НАЧАЛО НОВОГО КОДА
                // =======================================================

                // Проверяем, реализует ли устройство наш интерфейс IDevice
                if (deviceObj instanceof IDevice device) {
                    // Получаем актуальный мир и позицию, на которую смотрит игрок
                    ServerWorld world = player.getServerWorld();
                    net.minecraft.util.hit.HitResult hit = player.raycast(5.0, 0.0f, false);
                    net.minecraft.util.math.BlockPos targetPos = null;
                    if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                        targetPos = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
                    }

                    // ВЫПОЛНЯЕМ ПРИВЯЗКУ!
                    device.rebind(player, world, targetPos);
                }

                // =======================================================
                //          КОНЕЦ НОВОГО КОДА
                // =======================================================

                try {
                    Object[] args = payload.getArgs();
                    Method methodToCall = Arrays.stream(deviceObj.getClass().getMethods())
                            .filter(m -> m.isAnnotationPresent(com.loracore.computer.api.Callback.class))
                            .filter(m -> m.getName().equals(payload.methodName()))
                            .findFirst()
                            .orElseThrow(() -> new NoSuchMethodException("Method '" + payload.methodName() + "' not found or not a @Callback."));

                    Object result = methodToCall.invoke(deviceObj, args);

                    String resultJson = DeviceMethodResultS2CPacket.resultToJson(result);
                    ServerPlayNetworking.send(player, new DeviceMethodResultS2CPacket(payload.requestId(), true, resultJson));

                } catch (Exception e) {
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    ServerPlayNetworking.send(player, new DeviceMethodResultS2CPacket(payload.requestId(), false, DeviceMethodResultS2CPacket.resultToJson(errorMsg)));
                }
            });
        });
    }
}