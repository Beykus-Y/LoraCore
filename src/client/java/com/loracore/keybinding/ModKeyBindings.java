package com.loracore.keybinding;

import com.loracore.gui.QuestLogScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {

    public static KeyBinding openQuestLogKey;

    public static void register() {
        openQuestLogKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aiassist.open_quest_log", // Ключ для перевода в lang файле
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.aiassist.main" // Категория в настройках управления
        ));

        registerKeyInputs();
    }

    private static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openQuestLogKey.wasPressed()) {
                                client.setScreen(new QuestLogScreen());
            }
        });
    }
}