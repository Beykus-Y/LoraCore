package com.loracore.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loracore.LoraCoreMod;
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

    public static final int SCREEN_WIDTH = 480;
    public static final int SCREEN_HEIGHT = 270;
    private static final int CHAR_RENDER_WIDTH = 7;

    // Внутренний класс для хранения данных о символе:
    // какая картинка и какие координаты на ней
    private static class Glyph {
        final BufferedImage sourceImage;
        final int x, y, width, height;

        Glyph(BufferedImage source, int x, int y, int w, int h) {
            this.sourceImage = source; this.x = x; this.y = y; this.width = w; this.height = h;
        }
    }

    // Единая карта для всех символов из всех файлов шрифтов
    private final Map<Character, Glyph> glyphMap = new HashMap<>();

    public ClientFont(ResourceManager resourceManager) {
        // Загружаем все наши шрифты
        loadFontProvider(resourceManager, "/assets/loracore/font/eng.json");
        loadFontProvider(resourceManager, "/assets/loracore/font/other.json");
        LoraCoreMod.LOGGER.info("Клиентский шрифт загружен, {} символов доступно.", glyphMap.size());
    }

    private void loadFontProvider(ResourceManager resourceManager, String jsonPath) {
        try {
            // Используем ResourceManager для загрузки ресурсов
            var jsonResource = resourceManager.getResource(new net.minecraft.util.Identifier("loracore", jsonPath.substring("/assets/loracore/".length())));
            if (jsonResource.isEmpty()) {
                LoraCoreMod.LOGGER.error("Не найден файл определения шрифта: {}", jsonPath);
                return;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(jsonResource.get().getInputStream(), StandardCharsets.UTF_8), JsonObject.class);
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

                BufferedImage image = ImageIO.read(imageResource.get().getInputStream());
                JsonArray charRows = provider.getAsJsonArray("chars");
                int gridHeight = charRows.size();
                int gridWidth = charRows.get(0).getAsString().length();
                int glyphWidth = image.getWidth() / gridWidth;
                int glyphHeight = image.getHeight() / gridHeight;

                for (int row = 0; row < gridHeight; row++) {
                    String charRow = charRows.get(row).getAsString();
                    for (int col = 0; col < charRow.length(); col++) {
                        char c = charRow.charAt(col);
                        if (c == '\u0000') continue; // Пропускаем пустые символы

                        int glyphX = col * glyphWidth;
                        int glyphY = row * glyphHeight;
                        glyphMap.put(c, new Glyph(image, glyphX, glyphY, glyphWidth, glyphHeight));
                    }
                }
            }
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Критическая ошибка при загрузке провайдера шрифта {}", jsonPath, e);
        }
    }

    public void drawString(NativeImage targetImage, int x, int y, String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        int currentX = x;
        for (char c : text.toCharArray()) {
            drawChar(targetImage, currentX, y, c, color);
            currentX += CHAR_RENDER_WIDTH;
        }
    }

    private void drawChar(NativeImage targetImage, int x, int y, char c, int color) {
        Glyph glyph = glyphMap.get(c);
        if (glyph == null) {
            return;
        }

        // Конвертируем ARGB в ABGR для NativeImage
        int abgr = (color & 0xFF000000) | ((color & 0x00FF0000) >> 16) | (color & 0x0000FF00) | ((color & 0x000000FF) << 16);

        for (int j = 0; j < glyph.height; j++) {
            for (int i = 0; i < glyph.width; i++) {
                int pixelColor = glyph.sourceImage.getRGB(glyph.x + i, glyph.y + j);
                if ((pixelColor >> 24) != 0x00) { // Если пиксель не полностью прозрачный
                    int screenX = x + i;
                    int screenY = y + j;

                    if (screenX >= 0 && screenX < targetImage.getWidth() && screenY >= 0 && screenY < targetImage.getHeight()) {
                        targetImage.setColor(screenX, screenY, abgr);
                    }
                }
            }
        }
    }
}
