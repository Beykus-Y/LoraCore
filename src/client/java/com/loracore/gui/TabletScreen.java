// Полный исправленный файл: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreClient;
import com.loracore.LoraCoreMod; // Импортируем, чтобы получить MOD_ID
import com.loracore.computer.ClientVFS;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.jkernel.JavaRuntime;
import com.loracore.computer.lualibs.LuaRuntime;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
// ИСПРАВЛЕНИЕ 1: Используем NativeImageBackedTexture вместо DynamicTexture
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.util.UUID;

public class TabletScreen extends Screen {

    private final UUID tabletUuid;
    private final ClientVFS vfs;

    private static final int BACKGROUND_OVERLAY_COLOR = 0xB0000000;
    private static final int TABLET_BORDER_COLOR = 0xFF0A0A0A;
    private static final int TABLET_BG_COLOR = 0xFF1E1E1E;

    public static final int SCREEN_PIXEL_WIDTH = 480;
    public static final int SCREEN_PIXEL_HEIGHT = 270;

    private static final String LUA_BOOT_PATH = "os/recovery.lua";
    private static final String JAVA_BOOT_PATH = "/boot/kernel.jar";

    private enum State { LOADING, RUNNING, CRASHED, HALTED }
    private State currentState = State.LOADING;
    private String statusMessage = "Initializing...";

    private IRuntimeEnvironment runtime;

    private int tabletX, tabletY, tabletWidth, tabletHeight;

    // ИСПРАВЛЕНИЕ 1: Меняем тип с DynamicTexture на конкретный класс NativeImageBackedTexture
    private NativeImage screenImage;
    private NativeImageBackedTexture screenTexture;
    private Identifier screenTextureId;

    public TabletScreen(ClientVFS vfs, UUID tabletUuid) {
        super(Text.literal("LoraOS"));
        this.vfs = vfs;
        this.tabletUuid = tabletUuid;
        // ИСПРАВЛЕНИЕ 2: Создаем уникальный Identifier для текстуры нашего экрана
        this.screenTextureId = new Identifier(LoraCoreMod.MOD_ID, "tablet_screen/" + this.tabletUuid.toString());
    }

    public UUID getTabletUuid() { return this.tabletUuid; }
    public int getTabletPixelWidth() { return SCREEN_PIXEL_WIDTH; }
    public int getTabletPixelHeight() { return SCREEN_PIXEL_HEIGHT; }

    @Override
    protected void init() {
        super.init();
        LoraCoreClient.setActiveVfsInstance(this.vfs);
        calculateTabletDimensions();

        // ИСПРАВЛЕНИЕ 3: Полностью переработана логика создания текстуры
        // Создаем "холст" для рисования (NativeImage)
        this.screenImage = new NativeImage(NativeImage.Format.RGBA, SCREEN_PIXEL_WIDTH, SCREEN_PIXEL_HEIGHT, false);
        // Создаем текстуру, которая будет отображать наш холст
        this.screenTexture = new NativeImageBackedTexture(this.screenImage);
        // Регистрируем нашу текстуру в менеджере текстур игры, чтобы ее можно было рисовать
        this.client.getTextureManager().registerTexture(this.screenTextureId, this.screenTexture);

        initializeRuntime();
    }

    private void initializeRuntime() {
        this.currentState = State.LOADING;
        this.statusMessage = "Reading boot config...";

        vfs.existsAsync(JAVA_BOOT_PATH).whenComplete((exists, error) -> {
            client.execute(() -> {
                if (error == null && exists.toboolean()) {
                    this.statusMessage = "JAVA mode detected. Booting kernel...";
                    this.runtime = new JavaRuntime(this, vfs);
                    this.runtime.boot(JAVA_BOOT_PATH);
                } else {
                    this.statusMessage = "LUA mode detected. Booting recovery...";
                    this.runtime = new LuaRuntime(this, vfs);
                    this.runtime.boot(LUA_BOOT_PATH);
                }
                this.currentState = State.RUNNING;
            });
        });
    }

    public void onScreenUpdate(byte[] pixelBuffer) {
        // ИСПРАВЛЕНИЕ: Добавляем проверку на null для pixelBuffer
        if (this.screenImage == null || this.screenTexture == null || pixelBuffer == null || pixelBuffer.length != (SCREEN_PIXEL_WIDTH * SCREEN_PIXEL_HEIGHT * 4)) {
            return;
        }

        // ИСПРАВЛЕНИЕ 4: Правильный способ обновить содержимое текстуры
        // Мы проходим по каждому пикселю и устанавливаем его цвет.
        // Сервер присылает данные в формате RGBA, а NativeImage хранит их в формате ABGR.
        // Этот код корректно преобразует формат.
        for (int y = 0; y < SCREEN_PIXEL_HEIGHT; y++) {
            for (int x = 0; x < SCREEN_PIXEL_WIDTH; x++) {
                int i = (y * SCREEN_PIXEL_WIDTH + x) * 4;
                int r = pixelBuffer[i] & 0xFF;
                int g = pixelBuffer[i + 1] & 0xFF;
                int b = pixelBuffer[i + 2] & 0xFF;
                int a = pixelBuffer[i + 3] & 0xFF;
                this.screenImage.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }

        // Загружаем измененные пиксели на видеокарту
        this.screenTexture.upload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, BACKGROUND_OVERLAY_COLOR);
        context.fill(tabletX, tabletY, tabletX + tabletWidth, tabletY + tabletHeight, TABLET_BORDER_COLOR);
        context.fill(tabletX + 2, tabletY + 2, tabletX + tabletWidth - 2, tabletY + tabletHeight - 2, TABLET_BG_COLOR);

        if (screenTextureId != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            // ИСПРАВЛЕНИЕ 5: Используем правильный Identifier вместо строки
            context.drawTexture(screenTextureId, tabletX + 2, tabletY + 2, 0, 0, tabletWidth - 4, tabletHeight - 4, tabletWidth - 4, tabletHeight - 4);
            RenderSystem.disableBlend();
        }

        if (currentState == State.LOADING || currentState == State.CRASHED) {
            context.getMatrices().push();
            context.getMatrices().translate(tabletX + (tabletWidth / 2f), tabletY + (tabletHeight / 2f), 10);
            if (currentState == State.LOADING) {
                context.drawCenteredTextWithShadow(textRenderer, statusMessage, 0, 0, 0xFFFFFF);
            } else {
                context.drawCenteredTextWithShadow(textRenderer, "FATAL ERROR", 0, -10, 0xFF5555);
                context.drawCenteredTextWithShadow(textRenderer, statusMessage, 0, 10, 0xFFFFFF);
            }
            context.getMatrices().pop();
        }
    }

    // Перерисовываем текстуру планшета. Измененный метод.
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Мы больше не вызываем super.renderBackground(context, mouseX, mouseY, delta);
        // чтобы избежать затемнения экрана по умолчанию.
        // Вместо этого, если нужно, можно отрисовать свой фон.
        // В данном случае мир за экраном будет виден как есть.
    }


    @Override
    public void tick() {
        if (currentState == State.RUNNING && runtime != null) {
            runtime.tick();
            if (runtime.getCrashMessage() != null) {
                this.currentState = State.CRASHED;
                this.statusMessage = runtime.getCrashMessage();
                runtime.shutdown();
            } else if (!runtime.isRunning()) {
                this.currentState = State.HALTED;
            }
        }
    }

    public void reboot() {
        if (this.client != null) {
            this.client.execute(() -> this.client.setScreen(new TabletScreen(this.vfs, this.tabletUuid)));
        }
    }

    @Override
    public void close() {
        // ИСПРАВЛЕНИЕ 6: Безопасно закрываем/удаляем текстуру при закрытии экрана
        if (this.client != null && this.screenTextureId != null) {
            this.client.getTextureManager().destroyTexture(this.screenTextureId);
        }
        if (this.screenTexture != null) {
            this.screenTexture.close();
        }

        super.close();
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

    // ... Остальные методы ввода (charTyped, mouseClicked, etc.) без изменений ...
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (runtime != null && currentState == State.RUNNING) return runtime.onCharTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (runtime != null && currentState == State.RUNNING) {
            double localX = (mouseX - (tabletX + 2)) * ((double)SCREEN_PIXEL_WIDTH / (tabletWidth - 4));
            double localY = (mouseY - (tabletY + 2)) * ((double)SCREEN_PIXEL_HEIGHT / (tabletHeight - 4));
            if (localX >= 0 && localX < SCREEN_PIXEL_WIDTH && localY >= 0 && localY < SCREEN_PIXEL_HEIGHT) {
                return runtime.onMouseClicked(localX, localY, button);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() { return false; }

    private void calculateTabletDimensions() {
        this.tabletHeight = (int) (this.height * 0.9);
        this.tabletWidth = (int) (this.tabletHeight * ((double)SCREEN_PIXEL_WIDTH / SCREEN_PIXEL_HEIGHT));
        if (this.tabletWidth > this.width * 0.95) {
            this.tabletWidth = (int) (this.width * 0.95);
            this.tabletHeight = (int) (this.tabletWidth * ((double)SCREEN_PIXEL_HEIGHT / SCREEN_PIXEL_WIDTH));
        }
        this.tabletX = (this.width - this.tabletWidth) / 2;
        this.tabletY = (this.height - this.tabletHeight) / 2;
    }
}