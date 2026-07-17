// Полный исправленный файл: src/client/java/com/loracore/LoraCoreClient.java
package com.loracore;

import com.loracore.api.ClientApi;
import com.loracore.api.GpuApi;
import com.loracore.gui.AskChatScreen;
import com.loracore.gui.TabletScreen;
import com.loracore.keybinding.ModKeyBindings;
import com.loracore.network.BootTabletS2CPacket;
import com.loracore.network.SpawnDebugTabletC2SPacket;
import com.loracore.network.SystemMetricsS2CPacket;
import com.loracore.network.graphics.GpuCommandC2SPacket;
import com.loracore.network.graphics.ScreenUpdateS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class LoraCoreClient implements ClientModInitializer {
    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(LoraCoreMod.MOD_ID + "_CLIENT");

    private static boolean openAskScreenFlag = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("LoraCore Client Initializing...");
        registerPacketHandlers();
        ModKeyBindings.register();
        registerClientCommands();
        registerTickEvents();

        // --- Инициализация API ---
        ClientApi.renderThreadExecutor = MinecraftClient.getInstance()::execute;
        // Реализуем действие для отправки GPU команд
        GpuApi.sendCommandAction = (uuid, command) -> ClientPlayNetworking.send(new GpuCommandC2SPacket(uuid, command));

        LOGGER.info("LoraCore Client successfully initialized!");
    }

    private void registerPacketHandlers() {
        // Обработчик для загрузки планшета
        ClientPlayNetworking.registerGlobalReceiver(BootTabletS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                LOGGER.info("LoraCoreClient: Получен пакет BootTabletS2CPacket"); // Сообщение тоже можно упростить

                TabletScreen tabletScreen = new TabletScreen(payload.fileSystemUuid(), payload.tabletUuid());
                // Строка tabletScreen.setTabletRamKb(...) была удалена
                context.client().setScreen(tabletScreen);
            });
        });
        // ИСПРАВЛЕНИЕ: Обработчик обновления экрана теперь находится ВНУТРИ метода
        ClientPlayNetworking.registerGlobalReceiver(ScreenUpdateS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                    if (currentScreen instanceof TabletScreen tabletScreen) {
                        // Проверяем, что пакет предназначен для текущего открытого планшета
                        if (tabletScreen.getTabletUuid().equals(payload.tabletUuid())) {
                            tabletScreen.onScreenUpdate(payload.pixelBuffer());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing screen update packet: {}", e.getMessage(), e);
                }
            });
        });
        // Обработчик метрик системы
        ClientPlayNetworking.registerGlobalReceiver(SystemMetricsS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                if (currentScreen instanceof TabletScreen tabletScreen) {
                    if (tabletScreen.getTabletUuid().equals(payload.tabletUuid())) {
                        tabletScreen.updateMetrics(
                            payload.cpuLoad(),
                            payload.ramUsedKb(),
                            payload.ramTotalKb(),
                            payload.diskQueue(),
                            payload.uptimeSeconds(),
                            payload.currentPc(),
                            payload.tabletUuidStr(),
                            payload.fsUuidStr()
                        );
                    }
                }
            });
        });
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("ask")
                            .executes(context -> {
                                openAskScreenFlag = true;
                                return 1;
                            })
            );
            dispatcher.register(
                    literal("loracore_debug")
                            .executes(context -> {
                                var player = MinecraftClient.getInstance().player;
                                if (player == null) {
                                    return 0;
                                }
                                // Only work if the player is in Creative mode
                                if (!player.getAbilities().creativeMode) {
                                    player.sendMessage(net.minecraft.text.Text.literal("This command requires Creative mode.").formatted(net.minecraft.util.Formatting.RED), false);
                                    return 0;
                                }
                                // Send the SpawnDebugTabletC2SPacket to the server
                                ClientPlayNetworking.send(new SpawnDebugTabletC2SPacket());
                                return 1;
                            })
            );
        });
    }

    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openAskScreenFlag) {
                openAskScreenFlag = false;
                client.setScreen(new AskChatScreen());
            }
        });
    }
}
