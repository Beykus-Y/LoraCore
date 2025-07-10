package com.loracore.service; // ИЗМЕНЕНИЕ: Пакет исправлен на com.loracore

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Optional;
import java.util.stream.Collectors;

public class RecipeService {

    /**
     * Пытается найти рецепт, анализируя полный текст вопроса от игрока.
     * Ищет в вопросе названия предметов из игры.
     * @param query Полный текст вопроса.
     * @param player Игрок для контекста мира.
     * @return Optional со строкой-описанием рецепта, если он был найден.
     */
    public static Optional<String> findRecipeFromQuery(String query, ServerPlayerEntity player) {
        String lowerCaseQuery = query.toLowerCase();

        Item targetItem = null;
        int longestMatch = 0;

        for (Item item : Registries.ITEM) {
            String itemName = item.getName().getString().toLowerCase();
            if (lowerCaseQuery.contains(itemName) && itemName.length() > longestMatch) {
                targetItem = item;
                longestMatch = itemName.length();
            }
        }

        if (targetItem == null) {
            return Optional.empty(); // Предмет не упоминался в вопросе
        }

        // ИЗМЕНЕНИЕ: Проверка на null для предотвращения NPE
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Optional.empty();
        }

        // ИЗМЕНЕНИЕ: Создаем final переменную для использования в лямбде
        final Item finalTargetItem = targetItem;
        RecipeManager recipeManager = server.getRecipeManager();

        Optional<RecipeEntry<?>> recipeEntry = recipeManager.values().stream()
                .filter(r -> r.value().getResult(player.getWorld().getRegistryManager()).getItem() == finalTargetItem)
                .findFirst();

        return recipeEntry.map(entry -> formatRecipeForAI(entry, player));
    }

    private static String formatRecipeForAI(RecipeEntry<?> recipeEntry, ServerPlayerEntity player) {
        Recipe<?> recipe = recipeEntry.value();
        ItemStack output = recipe.getResult(player.getWorld().getRegistryManager());
        StringBuilder context = new StringBuilder();

        context.append("Информация о рецепте для '").append(output.getName().getString()).append("'.\n");
        context.append("ID предмета: ").append(Registries.ITEM.getId(output.getItem())).append(".\n");
        context.append("Количество на выходе: ").append(output.getCount()).append(".\n");

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            context.append("Тип рецепта: упорядоченный (верстак).\n");
            context.append("Схема крафта (").append(shapedRecipe.getWidth()).append("x").append(shapedRecipe.getHeight()).append("):\n");
            for (int i = 0; i < shapedRecipe.getHeight(); i++) {
                for (int j = 0; j < shapedRecipe.getWidth(); j++) {
                    int index = i * shapedRecipe.getWidth() + j;
                    Ingredient ingredient = (index < shapedRecipe.getIngredients().size()) ? shapedRecipe.getIngredients().get(index) : Ingredient.EMPTY;
                    context.append("[").append(getIngredientName(ingredient)).append("] ");
                }
                context.append("\n");
            }
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            context.append("Тип рецепта: свободный (верстак).\n");
            context.append("Необходимые ингредиенты (порядок не важен): ");
            String ingredients = shapelessRecipe.getIngredients().stream()
                    .map(RecipeService::getIngredientName)
                    .collect(Collectors.joining(", "));
            context.append(ingredients).append(".\n");
        } else if (recipe instanceof AbstractCookingRecipe cookingRecipe) { // ИЗМЕНЕНИЕ: Проверяем общий тип
            context.append("Тип рецепта: переплавка (печь, коптильня или плавильня).\n");
            // ИЗМЕНЕНИЕ: Используем getFirst() и убираем избыточный каст
            Ingredient input = cookingRecipe.getIngredients().getFirst();
            context.append("Ингредиент для переплавки: ").append(getIngredientName(input)).append(".\n");
        } else {
            context.append("Тип рецепта не поддерживается для детального описания.\n");
        }

        return context.toString();
    }

    private static String getIngredientName(Ingredient ingredient) {
        if (ingredient.isEmpty()) return "пусто";
        ItemStack[] matchingStacks = ingredient.getMatchingStacks();
        if (matchingStacks.length == 0) return "неизвестный ингредиент";

        String baseName = matchingStacks[0].getName().getString();
        return matchingStacks.length > 1 ? baseName + " (или аналог)" : baseName;
    }
}