// Полный исправленный файл: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreClient;
import com.loracore.computer.ClientVFS;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.Terminal;
import com.loracore.computer.TerminalRenderer;
import com.loracore.computer.jkernel.JavaRuntime;
import com.loracore.computer.lualibs.LuaRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import com.loracore.api.ClientApi;

// ИСПРАВЛЕНИЕ: Класс теперь реализует интерфейс Terminal
public class TabletScreen extends Screen implements Terminal {

    private static final int BACKGROUND_OVERLAY_COLOR = 0xB0000000;
    private static final int TABLET_BORDER_COLOR = 0xFF0A0A0A;
    private static final int TABLET_BG_COLOR = 0xFF1E1E1E;
    private static final String CONFIG_PATH = "/etc/loracore.conf";
    private static final String LUA_BOOT_PATH = "os/recovery.lua";
    private static final String JAVA_BOOT_PATH = "/boot/kernel.jar";

    private enum State { LOADING, RUNNING, CRASHED, HALTED }
    private State currentState = State.LOADING;
    private String statusMessage = "Initializing...";

    private final ClientVFS vfs;
    private IRuntimeEnvironment runtime;

    private int tabletX, tabletY, tabletWidth, tabletHeight;

    public int getTabletWidth() { return tabletWidth; }
    public int getTabletHeight() { return tabletHeight; }
    public MinecraftClient getClient() { return this.client; }
    public TextRenderer getTextRenderer() { return this.textRenderer; }

    public TabletScreen(ClientVFS vfs) {
        super(Text.literal("LoraOS"));
        this.vfs = vfs;
    }

    @Override
    protected void init() {
        super.init();
        LoraCoreClient.setActiveVfsInstance(this.vfs);
        calculateTabletDimensions();
        initializeRuntime();

    }

    private void initializeRuntime() {
        this.currentState = State.LOADING;
        this.statusMessage = "Reading boot config...";

        // ИСПРАВЛЕНИЕ: Используем асинхронный вызов, чтобы не замораживать игру.
        vfs.readAsync(CONFIG_PATH).whenComplete((configContentLua, error) -> {
            // Ответ от сервера пришел. Теперь мы можем безопасно продолжить
            // инициализацию в главном потоке игры.
            client.execute(() -> {
                String mode = "LUA"; // Режим по умолчанию
                if (error == null && configContentLua != null && !configContentLua.isnil()) {
                    if (configContentLua.tojstring().toUpperCase().contains("MODE=JAVA")) {
                        mode = "JAVA";
                    }
                }

                if ("JAVA".equals(mode)) {
                    this.statusMessage = "JAVA mode detected. Booting kernel...";
                    this.runtime = new JavaRuntime(this, vfs);
                    this.runtime.boot(JAVA_BOOT_PATH);
                } else {
                    this.statusMessage = "LUA mode detected. Booting...";
                    this.runtime = new LuaRuntime(this, vfs, textRenderer);

                    if (runtime instanceof LuaRuntime luaRuntime) {
                        // Инициализируем размеры терминала ДО запуска скрипта
                        luaRuntime.resizeTerminal(this.tabletWidth - 8, this.tabletHeight - 8);
                    }

                    // И только теперь запускаем скрипт
                    this.runtime.boot(LUA_BOOT_PATH);
                }

                this.currentState = State.RUNNING;
            });
        });
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Пусто
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, BACKGROUND_OVERLAY_COLOR);
        context.fill(tabletX, tabletY, tabletX + tabletWidth, tabletY + tabletHeight, TABLET_BORDER_COLOR);
        context.fill(tabletX + 2, tabletY + 2, tabletX + tabletWidth - 2, tabletY + tabletHeight - 2, TABLET_BG_COLOR);

        context.getMatrices().push();
        context.enableScissor(tabletX + 2, tabletY + 2, tabletX + tabletWidth - 2, tabletY + tabletHeight - 2);
        context.getMatrices().translate(tabletX + 4, tabletY + 4, 0);

        switch (currentState) {
            case LOADING:
                context.drawCenteredTextWithShadow(textRenderer, statusMessage, (width / 2) - (tabletX + 4), (height / 2) - (tabletY + 4), 0xFFFFFF);
                break;
            case RUNNING:
            case HALTED:
                if (runtime != null) {
                    runtime.render(context, mouseX - (tabletX + 4), mouseY - (tabletY + 4), delta);
                }
                break;
            case CRASHED:
                context.drawCenteredTextWithShadow(textRenderer, "FATAL ERROR", (width / 2) - (tabletX + 4), (height / 2) - 10 - (tabletY + 4), 0xFF5555);
                if (statusMessage != null) {
                    textRenderer.wrapLines(Text.literal(statusMessage), tabletWidth - 10).forEach((line) -> {
                        context.drawCenteredTextWithShadow(textRenderer, line, (width / 2) - (tabletX + 4), (height / 2) - (tabletY + 4), 0xFFFFFF);
                    });
                }
                break;
        }

        context.disableScissor();
        context.getMatrices().pop();
    }

    @Override
    public void tick() {
        if (currentState == State.RUNNING && runtime != null) {
            if (runtime.getCrashMessage() != null) {
                this.currentState = State.CRASHED;
                this.statusMessage = runtime.getCrashMessage();
            } else if (runtime.isRunning()) {
                runtime.tick();
            } else {
                this.currentState = State.HALTED;
            }
        }
    }

    @Override
    public void removed() {
        if (runtime != null) {
            runtime.shutdown();
        }
        LoraCoreClient.setActiveVfsInstance(null);
        super.removed();
    }

    @Override
    public void reboot() {
        // ПРАВИЛЬНО: Мы просим клиент выполнить смену экрана в его собственном потоке
        ClientApi.executeOnRenderThread(() -> {
            if (this.client != null) {
                this.client.setScreen(new TabletScreen(this.vfs));
            }
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (runtime != null && currentState == State.RUNNING) {
            return runtime.onKeyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (runtime != null && currentState == State.RUNNING) {
            return runtime.onKeyReleased(keyCode, scanCode, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (runtime != null && currentState == State.RUNNING) {
            return runtime.onCharTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    // --- Методы mouse... без изменений ---

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void calculateTabletDimensions() {
        this.tabletHeight = (int) (this.height * 0.9);
        this.tabletWidth = (int) (this.tabletHeight * (16.0 / 10.0));
        if (this.tabletWidth > this.width * 0.95) {
            this.tabletWidth = (int) (this.width * 0.95);
            this.tabletHeight = (int) (this.tabletWidth * (10.0 / 16.0));
        }
        this.tabletX = (this.width - this.tabletWidth) / 2;
        this.tabletY = (this.height - this.tabletHeight) / 2;
    }

    // --- ИСПРАВЛЕНИЕ: Реализация методов интерфейса Terminal ---

    private TerminalRenderer getTerminalRenderer() {
        if (runtime instanceof LuaRuntime luaRuntime) {
            return luaRuntime.getTerminalRenderer();
        }
        return null;
    }

    @Override public void print(String text) { if (getTerminalRenderer() != null) getTerminalRenderer().print(text); }
    @Override public void clear() { if (getTerminalRenderer() != null) getTerminalRenderer().clear(); }
    @Override public void clearLine() { if (getTerminalRenderer() != null) getTerminalRenderer().clearLine(); }
    @Override public void setCursorPos(int x, int y) { if (getTerminalRenderer() != null) getTerminalRenderer().setCursorPos(x, y); }
    @Override public void setCursorBlink(boolean enabled) { if (getTerminalRenderer() != null) getTerminalRenderer().setCursorBlink(enabled); }
    @Override public String read() { return getTerminalRenderer() != null ? getTerminalRenderer().read() : null; }
    @Override public void showCrashScreen(String message) { this.currentState = State.CRASHED; this.statusMessage = message; }
    @Override public void setTextColor(int color) { if (getTerminalRenderer() != null) getTerminalRenderer().setTextColor(color); }
    @Override public void setBackgroundColor(int color) { if (getTerminalRenderer() != null) getTerminalRenderer().setBackgroundColor(color); }
    @Override public int[] getCursorPos() { return getTerminalRenderer() != null ? getTerminalRenderer().getCursorPos() : new int[]{0,0}; }
    @Override public int[] getSize() { return getTerminalRenderer() != null ? getTerminalRenderer().getSize() : new int[]{0,0}; }
}