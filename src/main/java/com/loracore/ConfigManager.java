package com.loracore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("aiassist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                try (BufferedReader reader = Files.newBufferedReader(CONFIG_FILE)) {
                    ModConfig.instance = GSON.fromJson(reader, ModConfig.class);
                }
                // Если файл пуст или содержит некорректный JSON, fromJson вернет null
                if (ModConfig.instance == null) {
                    ModConfig.instance = ModConfig.createDefault();
                }
            } else {
                // Файл не существует, создаем новый конфиг по умолчанию и сохраняем его
                ModConfig.instance = ModConfig.createDefault();
                saveConfig();
            }
        } catch (IOException | JsonSyntaxException e) {
            LoraCoreMod.LOGGER.error("Не удалось загрузить конфигурацию, будут использованы значения по умолчанию.", e);
            ModConfig.instance = ModConfig.createDefault();
        }
    }

    public static void saveConfig() {
        // Мы сохраняем текущий статический экземпляр
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(ModConfig.instance, writer);
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("Не удалось сохранить конфигурацию!", e);
        }
    }
}