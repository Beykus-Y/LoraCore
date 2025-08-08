// Полный исправленный файл: src/main/java/com/loracore/service/ServerFont.java
package com.loracore.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loracore.LoraCoreMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ServerFont {

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

    public ServerFont() {
        // Загружаем все наши шрифты
        loadFontProvider("/assets/loracore/font/eng.json");
        loadFontProvider("/assets/loracore/font/other.json");
        LoraCoreMod.LOGGER.info("Серверный шрифт загружен, {} символов доступно.", glyphMap.size());
    }

    private void loadFontProvider(String jsonPath) {
        try (InputStream jsonStream = ServerFont.class.getResourceAsStream(jsonPath)) {
            if (jsonStream == null) {
                LoraCoreMod.LOGGER.error("Не найден файл определения шрифта: {}", jsonPath);
                return;
            }

            Gson gson = new Gson();
            JsonObject root = gson.fromJson(new InputStreamReader(jsonStream, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray providers = root.getAsJsonArray("providers");

            for (int i = 0; i < providers.size(); i++) {
                JsonObject provider = providers.get(i).getAsJsonObject();
                if (!provider.get("type").getAsString().equals("bitmap")) continue;

                String pngPath = provider.get("file").getAsString().replace("loracore:", "/assets/loracore/");
                try (InputStream imageStream = ServerFont.class.getResourceAsStream(pngPath)) {
                    if (imageStream == null) {
                        LoraCoreMod.LOGGER.error("Не найден PNG файл шрифта: {}", pngPath);
                        continue;
                    }

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
                            if (c == '\u0000') continue; // Пропускаем пустые символы

                            int glyphX = col * glyphWidth;
                            int glyphY = row * glyphHeight;
                            glyphMap.put(c, new Glyph(image, glyphX, glyphY, glyphWidth, glyphHeight));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Критическая ошибка при загрузке провайдера шрифта {}", jsonPath, e);
        }
    }

    public void drawString(byte[] buffer, int x, int y, String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        int currentX = x;
        for (char c : text.toCharArray()) {
            drawChar(buffer, currentX, y, c, color);
            currentX += CHAR_RENDER_WIDTH;
        }
    }

    private void drawChar(byte[] buffer, int x, int y, char c, int color) {
        Glyph glyph = glyphMap.get(c);
        if (glyph == null) {
            return;
        }

        byte r = (byte) ((color >> 16) & 0xFF);
        byte g = (byte) ((color >> 8) & 0xFF);
        byte b = (byte) (color & 0xFF);

        for (int j = 0; j < glyph.height; j++) {
            for (int i = 0; i < glyph.width; i++) {
                int pixelColor = glyph.sourceImage.getRGB(glyph.x + i, glyph.y + j);
                if ((pixelColor >> 24) != 0x00) { // Если пиксель не полностью прозрачный
                    int screenX = x + i;
                    int screenY = y + j;

                    if (screenX >= 0 && screenX < SCREEN_WIDTH && screenY >= 0 && screenY < SCREEN_HEIGHT) {
                        int index = (screenY * SCREEN_WIDTH + screenX) * 4;
                        buffer[index] = r;
                        buffer[index + 1] = g;
                        buffer[index + 2] = b;
                        buffer[index + 3] = (byte) 255;
                    }
                }
            }
        }
    }
}