package com.aiassist;

import com.aiassist.service.AiService;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModConfigScreen {

    private static Text statusText = Text.literal("Нажмите кнопку, чтобы получить список.").formatted(Formatting.GRAY);

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Настройки AI Мода"));

        builder.setSavingRunnable(ConfigManager::saveConfig);

        ConfigCategory openAiCategory = builder.getOrCreateCategory(Text.literal("OpenAI API"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Поля API URL и API Key без изменений
        openAiCategory.addEntry(entryBuilder.startStrField(Text.literal("API URL"), ModConfig.instance.API_URL)
                .setDefaultValue("https://api.openai.com/v1")
                .setTooltip(Text.literal("URL для запросов к API."))
                .setSaveConsumer(newValue -> ModConfig.instance.API_URL = newValue)
                .build());

        openAiCategory.addEntry(entryBuilder.startStrField(Text.literal("API Key"), ModConfig.instance.API_KEY)
                .setDefaultValue("")
                .setTooltip(Text.literal("Ваш секретный ключ от OpenAI."))
                .setSaveConsumer(newValue -> ModConfig.instance.API_KEY = newValue)
                .build());

        // Используем startAction, как вы предложили
        openAiCategory.addEntry(entryBuilder.startBooleanToggle(
                        Text.literal("Получить/обновить список моделей"),
                        false // Начальное значение, всегда false
                )
                .setSaveConsumer(value -> { // Игнорируем `value`, просто выполняем действие
                    MinecraftClient client = MinecraftClient.getInstance();
                    statusText = Text.literal("Загрузка...").formatted(Formatting.YELLOW);

                    client.execute(() -> client.setScreen(ModConfigScreen.create(parent)));

                    AiService.fetchModels().whenCompleteAsync((models, error) -> {
                        if (error != null) {
                            AiMod.LOGGER.error("Ошибка при получении списка моделей: ", error);
                            statusText = Text.literal("Ошибка: " + error.getMessage()).formatted(Formatting.RED);
                        } else {
                            statusText = Text.literal("Успешно загружено " + models.size() + " моделей.").formatted(Formatting.GREEN);

                            if (!models.isEmpty() && !models.contains(ModConfig.instance.MODEL_ID)) {
                                ModConfig.instance.MODEL_ID = models.get(0);
                            }
                        }
                        client.execute(() -> client.setScreen(ModConfigScreen.create(parent)));
                    }, client);
                })
                .setTooltip(Text.literal("Загружает доступные модели с сервера API.\nТребует введённый выше API ключ.\n(Это переключатель, который работает как кнопка)"))
                .build());

        openAiCategory.addEntry(entryBuilder.startTextDescription(statusText).build());

        // Динамическое поле для выбора модели без изменений
        if (AiService.getAvailableModels().isEmpty()) {
            openAiCategory.addEntry(entryBuilder.startStrField(Text.literal("Model ID"), ModConfig.instance.MODEL_ID)
                    .setDefaultValue("gpt-4o")
                    .setTooltip(Text.literal("ID модели. Нажмите кнопку выше, чтобы загрузить список для выбора."))
                    .setSaveConsumer(newValue -> ModConfig.instance.MODEL_ID = newValue)
                    .build());
        } else {
            openAiCategory.addEntry(entryBuilder.startStringDropdownMenu(Text.literal("Model ID"), ModConfig.instance.MODEL_ID)
                    .setSelections(AiService.getAvailableModels())
                    .setSuggestionMode(false)
                    .setDefaultValue(AiService.getAvailableModels().get(0))
                    .setTooltip(Text.literal("Выберите модель из списка, полученного от API."))
                    .setSaveConsumer(newValue -> ModConfig.instance.MODEL_ID = newValue)
                    .build());
        }

        return builder.build();
    }
}