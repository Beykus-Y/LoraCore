// Полный исправленный файл: src/client/java/com/loracore/LoraCoreClient.java
package com.loracore;

import com.loracore.api.ClientApi;
import com.loracore.computer.ClientVFS;
import com.loracore.computer.VirtualMachine;
import com.loracore.gui.AskChatScreen;
import com.loracore.gui.TabletScreen;
import com.loracore.keybinding.ModKeyBindings;
import com.loracore.network.BootTabletS2CPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.luaj.vm2.LuaValue;

import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class LoraCoreClient implements ClientModInitializer {
    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(LoraCoreMod.MOD_ID + "_CLIENT");

    private static boolean openAskScreenFlag = false;

    // ИСПРАВЛЕНИЕ: activeVM больше не нужен как глобальная статическая переменная,
    // так как вся логика теперь инкапсулирована в экранах.
    // Оставляем только activeVfsInstance для обработки ответов.
    private static ClientVFS activeVfsInstance;

    @Override
    public void onInitializeClient() {
        LOGGER.info("LoraCore Client Initializing...");
        registerPacketHandlers();
        ModKeyBindings.register();
        registerClientCommands();
        registerTickEvents();
        ClientApi.renderThreadExecutor = MinecraftClient.getInstance()::execute;
        LOGGER.info("LoraCore Client successfully initialized!");
    }


    public static void setActiveVfsInstance(ClientVFS vfs) {
        activeVfsInstance = vfs;
    }

    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(VfsResponseS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                // ИСПРАВЛЕНИЕ: Упрощена вся логика.
                // Теперь ЛЮБОЙ ответ от VFS направляется в активный экземпляр ClientVFS.
                // ClientVFS сам разберется, положить ответ в блокирующую очередь или завершить Future.
                if (activeVfsInstance != null) {
                    LuaValue responseValue = switch (payload.type()) {
                        case TRUE -> LuaValue.TRUE;
                        case FALSE -> LuaValue.FALSE;
                        case STRING, TABLE_JSON -> LuaValue.valueOf(payload.data());
                        default -> LuaValue.NIL;
                    };
                    // Просто передаем ответ в обработчик.
                    activeVfsInstance.handleResponse(payload.callbackId(), responseValue);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BootTabletS2CPacket.ID, (payload, context) -> {
            UUID fsUuid = payload.fileSystemUuid();
            context.client().execute(() -> {
                ClientVFS vfs = new ClientVFS(fsUuid);
                // Устанавливаем этот экземпляр VFS как активный, чтобы он мог получать ответы от сервера.
                setActiveVfsInstance(vfs);
                context.client().setScreen(new TabletScreen(vfs));
            });
        });
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("ask")
                        .executes(context -> {
                            openAskScreenFlag = true;
                            return 1;
                        })
        ));
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