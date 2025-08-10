// Расположение: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreClient;
import com.loracore.LoraCoreMod;
import com.loracore.computer.ClientVFS;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.jkernel.JavaRuntime;
import com.loracore.network.RequestTabletDataC2SPacket;
import com.loracore.network.input.CharTypedC2SPacket;
import com.loracore.network.input.KeyPressedC2SPacket;
import com.loracore.network.input.MouseClickedC2SPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

public class TabletScreen extends Screen {

    private final UUID tabletUuid;
    private final UUID fileSystemUuid;
    private IRuntimeEnvironment clientRuntime = null;

    private int tabletX, tabletY, tabletWidth, tabletHeight;

    private NativeImage screenImage;
    private NativeImageBackedTexture screenTexture;
    private Identifier screenTextureId;

    public TabletScreen(UUID fileSystemUuid, UUID tabletUuid) {
        super(Text.literal("LoraOS"));
        this.fileSystemUuid = fileSystemUuid;
        this.tabletUuid = tabletUuid;
        this.screenTextureId = new Identifier(LoraCoreMod.MOD_ID, "tablet_screen/" + this.tabletUuid.toString());
    }

    public UUID getTabletUuid() { return this.tabletUuid; }

    // Этот геттер нужен для JavaRuntime, чтобы он мог передать его в KernelManager
    public NativeImage getScreenImage() { return this.screenImage; }

    @Override
    protected void init() {
        super.init();
        calculateTabletDimensions();

        // 1. Создаем новые графические ресурсы для этого экземпляра экрана
        this.screenImage = new NativeImage(NativeImage.Format.RGBA, 480, 270, false);
        this.screenTexture = new NativeImageBackedTexture(this.screenImage);
        this.client.getTextureManager().registerTexture(this.screenTextureId, this.screenTexture);

        // ✅ ИСПРАВЛЕНИЕ 2: Проверяем, существует ли уже JavaRuntime.
        // Если да, значит, экран был пересоздан (например, из-за ресайза),
        // и нам нужно передать ему ссылку на новый NativeImage.
        if (this.clientRuntime instanceof JavaRuntime javaRuntime) {
            javaRuntime.reinitializeGraphics(this.screenImage);
        }
    }

    public void onScreenUpdate(byte[] pixelBuffer) {
        if (this.screenImage == null || this.screenTexture == null || pixelBuffer == null || pixelBuffer.length != (480 * 270 * 4)) {
            return;
        }
        // Если у нас активен клиентский рантайм, мы не должны принимать обновления от сервера.
        if (clientRuntime != null && clientRuntime.isRunning()) {
            return;
        }

        for (int y = 0; y < 270; y++) {
            for (int x = 0; x < 480; x++) {
                int i = (y * 480 + x) * 4;
                int r = pixelBuffer[i] & 0xFF;
                int g = pixelBuffer[i + 1] & 0xFF;
                int b = pixelBuffer[i + 2] & 0xFF;
                int a = pixelBuffer[i + 3] & 0xFF;
                this.screenImage.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        this.screenTexture.upload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xB0000000); // Overlay
        context.fill(tabletX, tabletY, tabletX + tabletWidth, tabletY + tabletHeight, 0xFF0A0A0A); // Border

        // Если у нас есть клиентский рантайм И он должен рендерить на клиенте, то он главный.
        if (clientRuntime != null && clientRuntime.needsClientSideRendering()) {
            clientRuntime.render(mouseX, mouseY, delta);
            this.screenTexture.upload();
        }

        // В любом случае (даже поверх клиентского рендера, если он был) рисуем текстуру.
        // Для клиентского рендера это покажет результат, для серверного - пришедшую картинку.
        if (screenTextureId != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            context.drawTexture(screenTextureId, tabletX + 2, tabletY + 2, 0, 0, tabletWidth - 4, tabletHeight - 4, tabletWidth - 4, tabletHeight - 4);
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void tick() {
        if (clientRuntime != null) {
            clientRuntime.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        if (clientRuntime != null) {
            return clientRuntime.onKeyPressed(keyCode, scanCode, modifiers);
        }

        ClientPlayNetworking.send(new KeyPressedC2SPacket(this.tabletUuid, keyCode, scanCode, modifiers));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (clientRuntime != null) {
            return clientRuntime.onCharTyped(chr, modifiers);
        }

        ClientPlayNetworking.send(new CharTypedC2SPacket(this.tabletUuid, chr, modifiers));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = (mouseX - (tabletX + 2)) * (480.0 / (tabletWidth - 4));
        double localY = (mouseY - (tabletY + 2)) * (270.0 / (tabletHeight - 4));

        if (localX >= 0 && localX < 480 && localY >= 0 && localY < 270) {
            if (clientRuntime != null) {
                return clientRuntime.onMouseClicked(localX, localY, button);
            }
            ClientPlayNetworking.send(new MouseClickedC2SPacket(this.tabletUuid, localX, localY, button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void switchToClientKernel(String kernelPath) {
        if (clientRuntime != null) {
            clientRuntime.shutdown();
        }
        LoraCoreClient.LOGGER.info("Переключение на клиентское ядро. Путь: {}", kernelPath);
        ClientVFS vfs = ClientVFS.getInstance(this.fileSystemUuid);
        boolean isOwner = true;
        clientRuntime = new JavaRuntime(vfs, this.tabletUuid, this.screenImage, isOwner, this);
        clientRuntime.boot(kernelPath);
    }

    @Override
    public void removed() {
        // ✅ ИСПРАВЛЕНИЕ 4: Теперь мы НЕ выключаем рантайм при закрытии экрана,
        // так как он должен "пережить" ресайз. Он будет выключен только в close().
        super.removed();
    }

    @Override
    public void close() {
        // ✅ ИСПРАВЛЕНИЕ 5: Полное выключение и очистка ресурсов происходит только здесь,
        // когда пользователь действительно закрывает экран (например, через Esc).
        if (clientRuntime != null) {
            clientRuntime.shutdown();
            clientRuntime = null; // Очищаем статическую ссылку
        }
        if (this.client != null && this.screenTextureId != null) {
            this.client.getTextureManager().destroyTexture(this.screenTextureId);
        }
        if (this.screenTexture != null) {
            this.screenTexture.close();
        }
        super.close();
    }

    private void calculateTabletDimensions() {
        this.tabletHeight = (int) (this.height * 0.9);
        this.tabletWidth = (int) (this.tabletHeight * (480.0 / 270.0));
        if (this.tabletWidth > this.width * 0.95) {
            this.tabletWidth = (int) (this.width * 0.95);
            this.tabletHeight = (int) (this.tabletWidth * (270.0 / 480.0));
        }
        this.tabletX = (this.width - this.tabletWidth) / 2;
        this.tabletY = (this.height - this.tabletHeight) / 2;
    }

    public void reboot() {
        if (this.client != null) {
            this.close();
            ClientPlayNetworking.send(new RequestTabletDataC2SPacket());
        }
    }

    @Override public boolean shouldPause() { return false; }
}