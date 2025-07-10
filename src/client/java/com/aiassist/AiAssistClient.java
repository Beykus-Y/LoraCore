package com.aiassist;

import com.aiassist.gui.AskScreen;
import com.aiassist.keybinding.ModKeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class AiAssistClient implements ClientModInitializer {
    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(AiMod.MOD_ID + "_CLIENT");

    // Флаг для отложенного открытия AskScreen
    private static boolean openAskScreenFlag = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("AI Assist Client Initializing...");

        // Регистрируем ваши keybindings (QuestLog и т.п.)
        ModKeyBindings.register();

        // Регистрируем команду /ask — устанавливаем флаг
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LOGGER.info("Registering client command /ask...");
            dispatcher.register(
                    literal("ask")
                            .executes(context -> {
                                LOGGER.info("/ask invoked — scheduling AskScreen for next tick");
                                openAskScreenFlag = true;
                                return 1;
                            })
            );
            LOGGER.info("/ask registration complete.");
        });

        // В конце каждого тика проверяем флаг и, если нужно, открываем экран
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openAskScreenFlag) {
                openAskScreenFlag = false;
                LOGGER.info("Opening AskScreen now (END_CLIENT_TICK).");
                client.setScreen(new AskScreen());
            }
        });
    }
}
