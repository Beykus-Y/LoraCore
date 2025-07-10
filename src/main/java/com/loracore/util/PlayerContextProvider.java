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

    // Приватный конструктор, чтобы предотвратить создание экземпляров утилитарного класса.
    private PlayerContextProvider() {}

    /**
     * Собирает и форматирует полную контекстную информацию об игроке для AI.
     * @param player Игрок, для которого собирается контекст.
     * @return Строка с информацией о состоянии игрока.
     */
    public static String getContextFor(ServerPlayerEntity player) {
        StringBuilder context = new StringBuilder();
        context.append("Ты - помощник по игре Minecraft. Твои ответы должны быть полезными в контексте игры.\n");
        context.append("Информация о текущем состоянии игрока:\n");
        context.append(String.format("Местоположение: X=%d, Y=%d, Z=%d%n", player.getBlockX(), player.getBlockY(), player.getBlockZ()));

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

        // Основной инвентарь
        player.getInventory().main.stream()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    context.append("- ").append(stack.getCount()).append("x ").append(stack.getName().getString()).append("\n");
                    itemsInInventory.incrementAndGet();
                });

        // Броня
        player.getInventory().armor.stream()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    context.append("- ").append(stack.getName().getString()).append(" (броня)\n");
                    itemsInInventory.incrementAndGet();
                });

        // Вторая рука
        ItemStack offhandStack = player.getStackInHand(Hand.OFF_HAND);
        if (!offhandStack.isEmpty()) {
            context.append("- ").append(offhandStack.getCount()).append("x ").append(offhandStack.getName().getString()).append(" (вторая рука)\n");
            itemsInInventory.incrementAndGet();
        }

        if (itemsInInventory.get() == 0) {
            context.append("- Инвентарь пуст.\n");
        }

        context.append("\n");
        return context.toString();
    }
}