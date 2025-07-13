package com.loracore.util;

import com.loracore.LoraCoreMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PromptManager {
    private static final Map<String, String> PROMPT_CACHE = new HashMap<>();
    private static final String PROMPTS_DIR = "prompts";

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(LoraCoreMod.MOD_ID, "prompt_reloader");
            }

            @Override
            public void reload(ResourceManager manager) {
                // --- НАЧАЛО ВРЕМЕННОГО ОТЛАДОЧНОГО КОДА ---
                LoraCoreMod.LOGGER.info("--- [DEBUG] Начало перезагрузки промптов. Сканирование всех data-ресурсов мода '{}'...", LoraCoreMod.MOD_ID);

                // Этот блок кода выведет в лог АБСОЛЮТНО ВСЕ файлы, которые игра находит
                // в папке 'data/loracore/' вашего мода.
                // Это поможет точно определить, включены ли файлы в сборку и где они лежат.
                manager.findResources("", path -> true).forEach((id, resource) -> {
                    if (id.getNamespace().equals(LoraCoreMod.MOD_ID)) {
                        LoraCoreMod.LOGGER.info("[DEBUG] Найден ресурс в data-pack: {}", id);
                    }
                });

                LoraCoreMod.LOGGER.info("--- [DEBUG] Поиск файлов с расширением .prompt в папке '{}'...", PROMPTS_DIR);
                // --- КОНЕЦ ВРЕМЕННОГО ОТЛАДОЧНОГО КОДА ---

                PROMPT_CACHE.clear();
                manager.findResources(PROMPTS_DIR, path -> path.getPath().endsWith(".prompt"))
                        .forEach((id, resource) -> {
                            try (InputStream stream = resource.getInputStream()) {
                                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                                // Путь будет "prompts/villager_personality.prompt", из него мы извлекаем ключ.
                                String key = id.getPath().replace(PROMPTS_DIR + "/", "").replace(".prompt", "");
                                PROMPT_CACHE.put(key, content);
                                // Сообщение изменено для ясности
                                LoraCoreMod.LOGGER.info("Успешно загружен промпт: '{}', содержимое кэшировано.", key);
                            } catch (Exception e) {
                                LoraCoreMod.LOGGER.error("Не удалось загрузить промпт: {}", id, e);
                            }
                        });

                // --- ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА ПОСЛЕ ЗАГРУЗКИ ---
                if (PROMPT_CACHE.isEmpty()) {
                    LoraCoreMod.LOGGER.error("[DEBUG] Кэш промптов пуст после перезагрузки. Файлы не были найдены или произошла ошибка чтения.");
                } else {
                    LoraCoreMod.LOGGER.info("[DEBUG] Кэш промптов успешно заполнен и содержит {} записей.", PROMPT_CACHE.size());
                }
            }
        });
    }

    public static String getPrompt(String key) {
        return PROMPT_CACHE.getOrDefault(key, "");
    }

    public static String getFormattedPrompt(String key, Object... args) {
        String template = getPrompt(key);
        if (template.isEmpty()) {
            LoraCoreMod.LOGGER.warn("Attempted to use an empty or non-existent prompt template for key: {}", key);
            return "";
        }
        return String.format(template, args);
    }
}