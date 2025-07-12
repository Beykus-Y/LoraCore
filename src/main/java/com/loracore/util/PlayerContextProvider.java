package com.loracore.util;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Утилитарный класс для создания контекстной строки о состоянии игрока.
 */
public final class PlayerContextProvider {

    private PlayerContextProvider() {}

    /**
     * Собирает и форматирует блок с ДИНАМИЧЕСКОЙ информацией об игроке для AI.
     * @param player Игрок, для которого собирается контекст.
     * @return Строка с актуальной информацией о состоянии игрока.
     */
    public static String getDynamicContextFor(ServerPlayerEntity player) {
        StringBuilder context = new StringBuilder();

        // ИЗМЕНЕНИЕ: Формируем отдельный, легко различимый блок системной информации
        context.append("\n\n--- СИСТЕМНАЯ ИНФОРМАЦИЯ (АКТУАЛЬНОЕ СОСТОЯНИЕ ИГРОКА) ---\n");
        context.append(String.format("Местоположение: X=%d, Y=%d, Z=%d\n", player.getBlockX(), player.getBlockY(), player.getBlockZ()));

        World world = player.getEntityWorld();
        Identifier dimensionId = world.getRegistryKey().getValue();
        context.append("Измерение: ").append(dimensionId).append("\n");

        world.getBiome(player.getBlockPos()).getKey()
                .map(RegistryKey::getValue)
                .ifPresentOrElse(
                        biomeId -> context.append("Биом: ").append(biomeId).append("\n"),
                        () -> context.append("Биом: unknown_biome\n")
                );

        context.append("Инвентарь игрока:\n");
        AtomicInteger itemsInInventory = new AtomicInteger(0);

        player.getInventory().main.stream()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    context.append(String.format("- %dx %s\n", stack.getCount(), stack.getName().getString()));
                    itemsInInventory.incrementAndGet();
                });

        player.getInventory().armor.stream()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    context.append(String.format("- %s (броня)\n", stack.getName().getString()));
                    itemsInInventory.incrementAndGet();
                });

        ItemStack offhandStack = player.getStackInHand(Hand.OFF_HAND);
        if (!offhandStack.isEmpty()) {
            context.append(String.format("- %dx %s (вторая рука)\n", offhandStack.getCount(), offhandStack.getName().getString()));
            itemsInInventory.incrementAndGet();
        }

        if (itemsInInventory.get() == 0) {
            context.append("- Инвентарь пуст.\n");
        }

        context.append("--- КОНЕЦ СИСТЕМНОЙ ИНФОРМАЦИИ ---\n");
        return context.toString();
    }
}