package com.loracore.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loracore.LoraCoreMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ClientFont {

    private static ClientFont INSTANCE;

    // Внутреннее разрешение для рендеринга
    public static final int CHAR_RENDER_WIDTH = 7;

    private static class Glyph {
        final BufferedImage sourceImage;
        final int x, y, width, height;

        Glyph(BufferedImage source, int x, int y, int w, int h) {
            this.sourceImage = source; this.x = x; this.y = y; this.width = w; this.height = h;
        }
    }

    private final Map<Character, Glyph> glyphMap = new HashMap<>();

    // Приватный конструктор
    private ClientFont(ResourceManager resourceManager) {
        loadFontProvider(resourceManager, "/assets/loracore/font/eng.json");
        loadFontProvider(resourceManager, "/assets/loracore/font/other.json");
        LoraCoreMod.LOGGER.info("Клиентский шрифт загружен, {} символов доступно.", glyphMap.size());
    }

    // Синглтон метод
    public static synchronized ClientFont getInstance(ResourceManager resourceManager) {
        if (INSTANCE == null) {
            INSTANCE = new ClientFont(resourceManager);
        }
        return INSTANCE;
    }

    // Метод для сброса (например, при перезагрузке ресурсов)
    public static synchronized void reload(ResourceManager resourceManager) {
        INSTANCE = new ClientFont(resourceManager);
    }

    private void loadFontProvider(ResourceManager resourceManager, String jsonPath) {
        try {
            var jsonResource = resourceManager.getResource(new net.minecraft.util.Identifier("loracore", jsonPath.substring("/assets/loracore/".length())));
            if (jsonResource.isEmpty()) {
                LoraCoreMod.LOGGER.error("Не найден файл определения шрифта: {}", jsonPath);
                return;
            }

            Gson gson = new Gson();
            try (InputStream is = jsonResource.get().getInputStream()) {
                JsonObject root = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
                JsonArray providers = root.getAsJsonArray("providers");

                for (int i = 0; i < providers.size(); i++) {
                    JsonObject provider = providers.get(i).getAsJsonObject();
                    if (!provider.get("type").getAsString().equals("bitmap")) continue;

                    String pngPath = provider.get("file").getAsString().replace("loracore:", "/assets/loracore/");
                    String pngIdentifier = pngPath.substring("/assets/loracore/".length());

                    var imageResource = resourceManager.getResource(new net.minecraft.util.Identifier("loracore", pngIdentifier));
                    if (imageResource.isEmpty()) {
                        LoraCoreMod.LOGGER.error("Не найден PNG файл шрифта: {}", pngPath);
                        continue;
                    }

                    try (InputStream imageStream = imageResource.get().getInputStream()) {
                        BufferedImage image = ImageIO.read(imageStream);
                        JsonArray charRows = provider.getAsJsonArray("chars");
                        int gridHeight = charRows.size();
                        int gridWidth = charRows.get(0).getAsString().length();
                        int glyphWidth = image.getWidth() / gridWidth;
                        int glyphHeight = image.getHeight() / gridHeight;

                        for (int row = 0; row < gridHeight; row++) {
                            String charRow = charRows.get(row).getAsString();
                            for (int col = 0; col < charRow.length(); col++) {
                                char c = charRow.charAt(col);
                                if (c == '\u0000') continue;

                                int glyphX = col * glyphWidth;
                                int glyphY = row * glyphHeight;
                                glyphMap.put(c, new Glyph(image, glyphX, glyphY, glyphWidth, glyphHeight));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Критическая ошибка при загрузке провайдера шрифта {}", jsonPath, e);
        }
    }

    public void drawString(NativeImage targetImage, int x, int y, String text, int color, float scale) {
        if (text == null || text.isEmpty()) return;

        int currentX = x;
        for (char c : text.toCharArray()) {
            drawChar(targetImage, currentX, y, c, color, scale);
            currentX += (int)(CHAR_RENDER_WIDTH * scale);
        }
    }

    private void drawChar(NativeImage targetImage, int startX, int startY, char c, int color, float scale) {
        Glyph glyph = glyphMap.get(c);
        if (glyph == null) return;

        // Конвертация цвета (MC ARGB -> NativeImage ABGR)
        int abgr = (color & 0xFF000000) |
                ((color & 0x00FF0000) >> 16) |
                (color & 0x0000FF00) |
                ((color & 0x000000FF) << 16);

        for (int j = 0; j < glyph.height; j++) {
            for (int i = 0; i < glyph.width; i++) {
                int pixelColor = glyph.sourceImage.getRGB(glyph.x + i, glyph.y + j);
                if ((pixelColor >> 24) != 0x00) {
                    int drawX = startX + (int)(i * scale);
                    int drawY = startY + (int)(j * scale);
                    int size = (int)Math.ceil(scale);

                    for (int dy = 0; dy < size; dy++) {
                        for (int dx = 0; dx < size; dx++) {
                            int px = drawX + dx;
                            int py = drawY + dy;
                            if (px >= 0 && px < targetImage.getWidth() && py >= 0 && py < targetImage.getHeight()) {
                                targetImage.setColor(px, py, abgr);
                            }
                        }
                    }
                }
            }
        }
    }
}