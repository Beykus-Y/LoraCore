// Полный исправленный файл: src/client/java/com/loracore/computer/lualibs/LuaRuntime.java
package com.loracore.computer.lualibs;

import com.loracore.LoraCoreClient;
import com.loracore.computer.*;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class LuaRuntime implements IRuntimeEnvironment {

    private final VirtualMachine vm;
    private final TerminalRenderer terminalRenderer;
    private final TextRenderer textRenderer;
    private final ClientVFS vfs;

    public LuaRuntime(TabletScreen parentScreen, ClientVFS vfs, TextRenderer textRenderer) {
        this.terminalRenderer = new TerminalRenderer(parentScreen);
        this.textRenderer = textRenderer;
        this.vfs = vfs;

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
                // Игнорируем ошибки, возвращаем null
            }
            return null;
        };

        IVirtualFileSystem syncVfsImpl = new SyncVFS(vfs);

        this.vm = new VirtualMachine(
                "lora_v1_lua",
                1024,
                parentScreen,
                loader,
                syncVfsImpl,
                vfs.getFsUuid()
        );

        // ИСПРАВЛЕНИЕ: Эта строка удалена, так как метод setActiveVM больше не существует.
        // LoraCoreClient.setActiveVM(this.vm);
    }

    public TerminalRenderer getTerminalRenderer() {
        return terminalRenderer;
    }

    public void resizeTerminal(int tabletWidth, int tabletHeight) {
        if (this.terminalRenderer != null) {
            this.terminalRenderer.resize(tabletWidth, tabletHeight);
        }
    }

    @Override
    public void boot(String bootPath) {
        String bootScriptContent;
        if (bootPath.startsWith("/")) {
            bootScriptContent = this.vfs.readBlocking(bootPath).tojstring();
        } else {
            bootScriptContent = vm.getResourceLoader().load(bootPath);
        }

        vm.start(bootScriptContent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        terminalRenderer.render(context, textRenderer, 0, 0);
    }

    @Override
    public void tick() {
        // Не используется
    }

    @Override
    public void shutdown() {
        // ИСПРАВЛЕНИЕ: Теперь LuaRuntime сам отвечает за выключение своей VM.
        // Он больше не обращается к глобальному методу в LoraCoreClient.
        if (this.vm != null) {
            this.vm.shutdown();
        }
        // Также сообщим LoraCoreClient, что VFS этого рантайма больше не активен.

    }

    @Override
    public boolean isRunning() {
        return vm.isRunning();
    }

    @Override
    public String getCrashMessage() {
        return vm.getCrashMessage();
    }

    // --- Логика ввода без изменений ---

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (modifiers == GLFW.GLFW_MOD_CONTROL && keyCode == GLFW.GLFW_KEY_C) {
            vm.pushEvent("signal", "interrupt");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            terminalRenderer.onEnterPressed();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            terminalRenderer.onBackspacePressed();
            return true;
        }
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

    @Override public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) { return false; }
    @Override public boolean onMouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) { return false; }
}