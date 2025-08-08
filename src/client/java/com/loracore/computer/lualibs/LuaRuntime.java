// Полный исправленный файл: src/client/java/com/loracore/computer/lualibs/LuaRuntime.java
package com.loracore.computer.lualibs;

import com.loracore.computer.*;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class LuaRuntime implements IRuntimeEnvironment {

    private final VirtualMachine vm;
    private final TerminalRenderer terminalRenderer;
    private final ClientVFS vfs;

    public LuaRuntime(TabletScreen parentScreen, ClientVFS vfs) {
        this.vfs = vfs;
        // Lua в текстовом режиме все еще использует TerminalRenderer как реализацию интерфейса Terminal
        this.terminalRenderer = new TerminalRenderer(parentScreen);
        UUID tabletUuid = parentScreen.getTabletUuid();

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
                // Игнорировать
            }
            return null;
        };

        // Создаем VM с правильными аргументами
        this.vm = new VirtualMachine(
                "lora_v1_lua",
                1024, // RAM
                terminalRenderer, // TerminalRenderer реализует интерфейс Terminal
                loader,
                vfs, // Блокирующая обертка над асинхронным VFS
                vfs.getFsUuid(),
                tabletUuid // <-- UUID самого планшета
        );
    }

    @Override
    public void boot(String bootPath) {
        String bootScriptContent;
        // Lua-режим для простоты всегда грузит скрипт восстановления из ресурсов
        if (vm.getResourceLoader() != null) {
            bootScriptContent = vm.getResourceLoader().load(bootPath);
            vm.start(bootScriptContent);
        } else {
            vm.setCrashState("ResourceLoader not available.");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Вся логика рендера теперь на сервере. Этот метод пуст.
        // Lua-скрипты отправляют команды через GPU API.
    }

    @Override
    public void tick() {
        if (vm.getCrashMessage() != null) {
            terminalRenderer.showCrashScreen(vm.getCrashMessage());
        }
    }

    @Override
    public void shutdown() {
        if (this.vm != null) {
            this.vm.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return vm.isRunning();
    }

    @Override
    public String getCrashMessage() {
        return vm.getCrashMessage();
    }

    // --- Обработка ввода для текстового режима ---
    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifiers == GLFW.GLFW_MOD_CONTROL && keyCode == GLFW.GLFW_KEY_C) {
            vm.pushEvent("signal", "interrupt");
            return true;
        }
        vm.pushEvent("key", keyCode);
        return true;
    }

    @Override
    public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        terminalRenderer.onCharTyped(chr);
        vm.pushEvent("char", String.valueOf(chr));
        return true;
    }

    // В текстовом режиме мышь не используется
    @Override public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) { return false; }
    @Override public boolean onMouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) { return false; }
}