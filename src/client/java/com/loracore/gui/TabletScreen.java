// Расположение: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreClient;
import com.loracore.LoraCoreMod;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.jkernel.JavaRuntime;
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

    // Внутреннее разрешение планшета (High DPI)
    public static final int INTERNAL_WIDTH = 960;
    public static final int INTERNAL_HEIGHT = 540;

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

    public NativeImage getScreenImage() { return this.screenImage; }

    @Override
    protected void init() {
        super.init();
        calculateTabletDimensions();

        // Всегда создаем текстуру высокого разрешения
        this.screenImage = new NativeImage(NativeImage.Format.RGBA, INTERNAL_WIDTH, INTERNAL_HEIGHT, false);

        // Заливаем черным при инициализации
        this.screenImage.fillRect(0, 0, INTERNAL_WIDTH, INTERNAL_HEIGHT, 0xFF000000);

        this.screenTexture = new NativeImageBackedTexture(this.screenImage);
        this.client.getTextureManager().registerTexture(this.screenTextureId, this.screenTexture);

        if (this.clientRuntime instanceof JavaRuntime javaRuntime) {
            javaRuntime.reinitializeGraphics(this.screenImage);
        }
    }

    public void onScreenUpdate(byte[] pixelBuffer) {
        if (this.screenImage == null || this.screenTexture == null || pixelBuffer == null) {
            return;
        }
        // Если работает Java-ядро, игнорируем серверные обновления (они низкого разрешения)
        if (clientRuntime != null && clientRuntime.isRunning()) {
            return;
        }

        // Сервер присылает 480x270. Нам нужно растянуть это на 960x540.
        int serverWidth = 480;
        int serverHeight = 270;

        if (pixelBuffer.length == serverWidth * serverHeight * 4) {
            // Апскейлинг 2x (Nearest Neighbor)
            for (int y = 0; y < serverHeight; y++) {
                for (int x = 0; x < serverWidth; x++) {
                    int i = (y * serverWidth + x) * 4;
                    int r = pixelBuffer[i] & 0xFF;
                    int g = pixelBuffer[i + 1] & 0xFF;
                    int b = pixelBuffer[i + 2] & 0xFF;
                    int a = pixelBuffer[i + 3] & 0xFF;
                    int color = (a << 24) | (b << 16) | (g << 8) | r;

                    // Рисуем блок 2x2 пикселя
                    int targetX = x * 2;
                    int targetY = y * 2;

                    this.screenImage.setColor(targetX, targetY, color);
                    this.screenImage.setColor(targetX + 1, targetY, color);
                    this.screenImage.setColor(targetX, targetY + 1, color);
                    this.screenImage.setColor(targetX + 1, targetY + 1, color);
                }
            }
        } else if (pixelBuffer.length == INTERNAL_WIDTH * INTERNAL_HEIGHT * 4) {
            // Если вдруг сервер прислал полное разрешение (на будущее)
            for (int y = 0; y < INTERNAL_HEIGHT; y++) {
                for (int x = 0; x < INTERNAL_WIDTH; x++) {
                    int i = (y * INTERNAL_WIDTH + x) * 4;
                    // ... стандартное копирование ...
                    int r = pixelBuffer[i] & 0xFF;
                    int g = pixelBuffer[i + 1] & 0xFF;
                    int b = pixelBuffer[i + 2] & 0xFF;
                    int a = pixelBuffer[i + 3] & 0xFF;
                    this.screenImage.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
        }

        this.screenTexture.upload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, 0xB0000000); // Overlay
        context.fill(tabletX, tabletY, tabletX + tabletWidth, tabletY + tabletHeight, 0xFF0A0A0A); // Border

        if (clientRuntime != null && clientRuntime.needsClientSideRendering()) {
            clientRuntime.render(mouseX, mouseY, delta);
            this.screenTexture.upload();
        }

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
        // ИСПРАВЛЕНО: Используем INTERNAL_WIDTH/HEIGHT (960x540) для расчета координат
        double localX = (mouseX - (tabletX + 2)) * ((double) INTERNAL_WIDTH / (tabletWidth - 4));
        double localY = (mouseY - (tabletY + 2)) * ((double) INTERNAL_HEIGHT / (tabletHeight - 4));

        if (localX >= 0 && localX < INTERNAL_WIDTH && localY >= 0 && localY < INTERNAL_HEIGHT) {
            if (clientRuntime != null) {
                return clientRuntime.onMouseClicked(localX, localY, button);
            }

            // Для Lua режима (который 480x270) нам нужно даунскейлить координаты перед отправкой
            // чтобы os.pullEvent получал корректные данные
            double serverX = localX / 2.0;
            double serverY = localY / 2.0;

            ClientPlayNetworking.send(new MouseClickedC2SPacket(this.tabletUuid, serverX, serverY, button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void switchToClientKernel(String kernelPath) {
        if (clientRuntime != null) {
            clientRuntime.shutdown();
        }
        LoraCoreClient.LOGGER.info("Переключение на клиентское ядро. Путь: {}", kernelPath);
        com.loracore.computer.ClientVFS vfs = com.loracore.computer.ClientVFS.getInstance(this.fileSystemUuid);
        boolean isOwner = true;
        clientRuntime = new JavaRuntime(vfs, this.tabletUuid, this.screenImage, isOwner, this);
        clientRuntime.boot(kernelPath);
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public void close() {
        if (clientRuntime != null) {
            clientRuntime.shutdown();
            clientRuntime = null;
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
        // Сохраняем соотношение сторон 16:9
        this.tabletWidth = (int) (this.tabletHeight * (16.0 / 9.0));
        if (this.tabletWidth > this.width * 0.95) {
            this.tabletWidth = (int) (this.width * 0.95);
            this.tabletHeight = (int) (this.tabletWidth * (9.0 / 16.0));
        }
        this.tabletX = (this.width - this.tabletWidth) / 2;
        this.tabletY = (this.height - this.tabletHeight) / 2;
    }

    public void reboot() {
        if (this.client != null) {
            this.close();
            ClientPlayNetworking.send(new com.loracore.network.RequestTabletDataC2SPacket());
        }
    }

    public void onDeviceResult(int requestId, boolean success, Object[] result) {
        if (clientRuntime instanceof JavaRuntime javaRuntime) {
            javaRuntime.getKernelManager().onDeviceResult(requestId, success, result);
        }
    }

    public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, String tabletUuidStr, String fsUuidStr) {
        if (clientRuntime instanceof JavaRuntime javaRuntime) {
            javaRuntime.getKernelManager().updateMetrics(cpuLoad, ramUsedKb, ramTotalKb, diskQueue, tabletUuidStr, fsUuidStr);
        }
    }

    @Override public boolean shouldPause() { return false; }
}