// Расположение: src/client/java/com/loracore/gui/TabletScreen.java
package com.loracore.gui;

import com.loracore.LoraCoreMod;
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
    private boolean debugOverlayEnabled = false;
    private final UUID tabletUuid;
    private final UUID fileSystemUuid;

    private int tabletX, tabletY, tabletWidth, tabletHeight;

    // Внутреннее разрешение планшета (High DPI)
    public static final int INTERNAL_WIDTH = 960;
    public static final int INTERNAL_HEIGHT = 540;

    private NativeImage screenImage;
    private NativeImageBackedTexture screenTexture;
    private Identifier screenTextureId;
    private double mCpuLoad = 0;
    private double mRamUsed = 0;
    private double mRamTotal = 0;
    private int mDiskQueue = 0;
    private int mCurrentPc = 0;
    private String mTabletUuid = "N/A";
    private String mFsUuid = "N/A";
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

        // Клиентское Java-ядро удалено; рендеринг выполняется сервером через VRAM
    }

    public void onScreenUpdate(byte[] pixelBuffer) {
        if (this.screenImage == null || this.screenTexture == null || pixelBuffer == null) {
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

        if (screenTextureId != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            context.drawTexture(screenTextureId, tabletX + 2, tabletY + 2, 0, 0, tabletWidth - 4, tabletHeight - 4, tabletWidth - 4, tabletHeight - 4);
            RenderSystem.disableBlend();
        }
        if (debugOverlayEnabled) {
            renderDebugOverlay(context);
        }
    }

    private void renderDebugOverlay(DrawContext context) {
        int padding = 5;
        int lineHeight = 10;
        int startX = this.width - 160; // Ширина панели
        int startY = 5;

        // Фон
        context.fill(startX - padding, startY - padding, this.width - padding, startY + (7 * lineHeight) + padding, 0x90000000);

        int y = startY;
        int colorVal = 0x00FF00; // Зеленый текст
        int colorLabel = 0xFFFFFF; // Белый текст

        // Заголовок
        context.drawText(textRenderer, "LoraCore Debug (F9)", startX, y, 0xFFAAAA00, false); y += lineHeight;

        // CPU
        String cpuText = String.format("%.1f%%", mCpuLoad * 100);
        int cpuColor = mCpuLoad > 0.9 ? 0xFF5555 : colorVal;
        context.drawText(textRenderer, Text.literal("CPU: ").append(Text.literal(cpuText).withColor(cpuColor)), startX, y, colorLabel, false); y += lineHeight;

        // RAM
        String ramText = String.format("%.0f / %.0f KB", mRamUsed, mRamTotal);
        context.drawText(textRenderer, Text.literal("RAM: ").append(Text.literal(ramText).withColor(colorVal)), startX, y, colorLabel, false); y += lineHeight;

        // PC (Instruction Pointer)
        String pcText = String.format("0x%04X", mCurrentPc);
        context.drawText(textRenderer, Text.literal("PC:  ").append(Text.literal(pcText).withColor(0x55FFFF)), startX, y, colorLabel, false); y += lineHeight;

        // Disk
        context.drawText(textRenderer, Text.literal("IO Queue: " + mDiskQueue), startX, y, mDiskQueue > 5 ? 0xFF5555 : colorVal, false); y += lineHeight;

        // UUIDs (уменьшенным шрифтом или просто обрезкой, так как длинные)
        context.getMatrices().push();
        float scale = 0.7f;
        context.getMatrices().scale(scale, scale, 1.0f);
        int scaledX = (int)(startX / scale);
        int scaledY = (int)(y / scale);

        context.drawText(textRenderer, "T-UUID: " + mTabletUuid, scaledX, scaledY, 0xAAAAAA, false);
        context.drawText(textRenderer, "FS-UUID: " + mFsUuid, scaledX, scaledY + 10, 0xAAAAAA, false);

        context.getMatrices().pop();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F9) {
            if (this.client != null && this.client.player != null && this.client.player.isCreative()) {
                debugOverlayEnabled = !debugOverlayEnabled;
                return true;
            }
        }

        ClientPlayNetworking.send(new KeyPressedC2SPacket(this.tabletUuid, keyCode, scanCode, modifiers));
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        ClientPlayNetworking.send(new CharTypedC2SPacket(this.tabletUuid, chr, modifiers));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ИСПРАВЛЕНО: Используем INTERNAL_WIDTH/HEIGHT (960x540) для расчета координат
        double localX = (mouseX - (tabletX + 2)) * ((double) INTERNAL_WIDTH / (tabletWidth - 4));
        double localY = (mouseY - (tabletY + 2)) * ((double) INTERNAL_HEIGHT / (tabletHeight - 4));

        if (localX >= 0 && localX < INTERNAL_WIDTH && localY >= 0 && localY < INTERNAL_HEIGHT) {
            // Серверный framebuffer использует логическое разрешение 480x270.
            double serverX = localX / 2.0;
            double serverY = localY / 2.0;

            ClientPlayNetworking.send(new MouseClickedC2SPacket(this.tabletUuid, serverX, serverY, button));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public void close() {
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

    public void onDeviceResult(int requestId, boolean success, Object[] result) { }

    public void updateMetrics(double cpuLoad, double ramUsedKb, double ramTotalKb, int diskQueue, long uptimeSeconds, int currentPc, String tabletUuidStr, String fsUuidStr) {
        this.mCpuLoad = cpuLoad;
        this.mRamUsed = ramUsedKb;
        this.mRamTotal = ramTotalKb;
        this.mDiskQueue = diskQueue;
        this.mCurrentPc = currentPc;
        this.mTabletUuid = tabletUuidStr;
        this.mFsUuid = fsUuidStr;
    }
    @Override public boolean shouldPause() { return false; }
}
