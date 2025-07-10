package com.loracore;

import com.loracore.service.AiService;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModConfigScreen {

    private static Text statusText = Text.translatable("config.aiassist.status.idle").formatted(Formatting.GRAY);

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("config.aiassist.title"));

        builder.setSavingRunnable(ConfigManager::saveConfig);

        ConfigCategory openAiCategory = builder.getOrCreateCategory(Text.translatable("config.aiassist.category.openai"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        openAiCategory.addEntry(entryBuilder.startStrField(Text.translatable("config.aiassist.option.apiUrl"), ModConfig.instance.API_URL)
                .setDefaultValue("https://api.openai.com/v1")
                .setTooltip(Text.translatable("config.aiassist.option.apiUrl.tooltip"))
                .setSaveConsumer(newValue -> ModConfig.instance.API_URL = newValue)
                .build());

        openAiCategory.addEntry(entryBuilder.startStrField(Text.translatable("config.aiassist.option.apiKey"), ModConfig.instance.API_KEY)
                .setDefaultValue("")
                .setTooltip(Text.translatable("config.aiassist.option.apiKey.tooltip"))
                .setSaveConsumer(newValue -> ModConfig.instance.API_KEY = newValue)
                .build());

        openAiCategory.addEntry(entryBuilder.startBooleanToggle(
                        Text.translatable("config.aiassist.button.fetchModels"),
                        false
                )
                .setSaveConsumer(value -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    statusText = Text.translatable("config.aiassist.status.loading").formatted(Formatting.YELLOW);

                    client.execute(() -> client.setScreen(ModConfigScreen.create(parent)));

                    AiService.fetchModels().whenCompleteAsync((models, error) -> {
                        if (error != null) {
                            AiMod.LOGGER.error("Ошибка при получении списка моделей: ", error);
                            statusText = Text.translatable("config.aiassist.status.error", error.getMessage()).formatted(Formatting.RED);
                        } else {
                            statusText = Text.translatable("config.aiassist.status.success", models.size()).formatted(Formatting.GREEN);
                            if (!models.isEmpty() && !models.contains(ModConfig.instance.MODEL_ID)) {
                                ModConfig.instance.MODEL_ID = models.get(0);
                            }
                        }
                        client.execute(() -> client.setScreen(ModConfigScreen.create(parent)));
                    }, client);
                })
                .setTooltip(Text.translatable("config.aiassist.button.fetchModels.tooltip"))
                .build());

        openAiCategory.addEntry(entryBuilder.startTextDescription(statusText).build());

        if (AiService.getAvailableModels().isEmpty()) {
            openAiCategory.addEntry(entryBuilder.startStrField(Text.translatable("config.aiassist.option.modelId"), ModConfig.instance.MODEL_ID)
                    .setDefaultValue("gpt-4o")
                    .setTooltip(Text.translatable("config.aiassist.option.modelId.tooltip.string"))
                    .setSaveConsumer(newValue -> ModConfig.instance.MODEL_ID = newValue)
                    .build());
        } else {
            openAiCategory.addEntry(entryBuilder.startStringDropdownMenu(Text.translatable("config.aiassist.option.modelId"), ModConfig.instance.MODEL_ID)
                    .setSelections(AiService.getAvailableModels())
                    .setSuggestionMode(false)
                    .setDefaultValue(AiService.getAvailableModels().get(0))
                    .setTooltip(Text.translatable("config.aiassist.option.modelId.tooltip.dropdown"))
                    .setSaveConsumer(newValue -> ModConfig.instance.MODEL_ID = newValue)
                    .build());
        }

        return builder.build();
    }
}