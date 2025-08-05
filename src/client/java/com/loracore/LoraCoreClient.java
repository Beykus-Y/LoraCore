// Файл: src/client/java/com/loracore/LoraCoreClient.java
package com.loracore;

import com.loracore.api.ClientApi;
import com.loracore.computer.ClientVFS;
import com.loracore.computer.ResourceLoader;
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
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.luaj.vm2.LuaValue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public class LoraCoreClient implements ClientModInitializer {
    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(LoraCoreMod.MOD_ID + "_CLIENT");

    private static boolean openAskScreenFlag = false;

    private static ClientVFS activeVfsInstance;
    private static TabletScreen activeTabletScreen = null;
    private static VirtualMachine activeVM = null;

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

    public static VirtualMachine getActiveVM() {
        return activeVM;
    }

    public static void shutdownActiveVM() {
        if (activeVM != null) {
            activeVM.shutdown();
            activeVM = null;
        }
        if (activeTabletScreen != null) {
            activeTabletScreen = null;
        }
        if (activeVfsInstance != null) {
            activeVfsInstance = null;
        }
    }

    public static void setActiveVfsInstance(ClientVFS vfs) {
        activeVfsInstance = vfs;
    }

    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(VfsResponseS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Теперь VFS обрабатывает ответ для блокирующих операций,
                // а VM - для асинхронных.
                if (activeVM != null) {
                    activeVM.resolveCallback(payload.callbackId(), payload.type(), payload.data());
                } else if (activeVfsInstance != null) {
                    LuaValue responseValue = switch (payload.type()) {
                        case TRUE -> LuaValue.TRUE;
                        case FALSE -> LuaValue.FALSE;
                        case STRING, TABLE_JSON -> LuaValue.valueOf(payload.data());
                        default -> LuaValue.NIL;
                    };
                    activeVfsInstance.handleResponse(payload.callbackId(), responseValue);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BootTabletS2CPacket.ID, (payload, context) -> {
            UUID fsUuid = payload.fileSystemUuid();
            String bootScriptPath = payload.bootScriptPath();
            String architecture = payload.architecture();
            int totalRamKb = payload.totalRamKb();

            context.client().execute(() -> {
                if (activeTabletScreen == null) {
                    ClientVFS vfs = new ClientVFS(fsUuid);
                    setActiveVfsInstance(vfs);
                    activeTabletScreen = new TabletScreen(vfs);

                    ResourceLoader loader = (path) -> {
                        try {
                            Identifier id = path.contains(":") ? new Identifier(path) : new Identifier("loracore", path);
                            Optional<Resource> resourceOpt = MinecraftClient.getInstance().getResourceManager().getResource(id);
                            if (resourceOpt.isPresent()) {
                                try (InputStream stream = resourceOpt.get().getInputStream()) {
                                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            }
                        } catch (Exception e) {
                            if (!path.startsWith("/")) {
                                LoraCoreMod.LOGGER.error("Failed to load internal resource: {}", path, e);
                            }
                        }
                        return null;
                    };

                    // --- НОВАЯ ЛОГИКА ЗАГРУЗКИ ---
                    String bootScriptContent;
                    if (bootScriptPath.startsWith("/")) {
                        // Если путь из VFS, используем блокирующий метод для загрузки
                        LuaValue content = vfs.readBlocking(bootScriptPath);
                        bootScriptContent = content.isnil() ? null : content.tojstring();
                    } else {
                        // Иначе это внутренний ресурс
                        bootScriptContent = loader.load(bootScriptPath);
                    }

                    // Создаем VM, передавая ей VFS как IVfsRequester
                    activeVM = new VirtualMachine(architecture, totalRamKb, activeTabletScreen, loader, vfs, fsUuid);
                    // Запускаем VM с уже загруженным кодом
                    activeVM.start(bootScriptContent);
                }
                context.client().setScreen(activeTabletScreen);
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