package com.aiassist;

import com.aiassist.network.ModNetworking;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import com.aiassist.service.AiService;
import com.aiassist.item.ModItems;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AiMod implements ModInitializer {
	public static final String MOD_ID = "aiassist";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, String> lastKnownStructurePosKeyForPlayer = new HashMap<>();
	private static final Map<UUID, Long> lastStructureCheckTickForPlayer = new HashMap<>();
	private static final int STRUCTURE_CHECK_INTERVAL_TICKS = 60;
	private static StructureNameManager structureNameManager;

	@Override
	public void onInitialize() {
		LOGGER.info("Загрузка мода AI Mod...");
		ConfigManager.loadConfig();
		ModNetworking.registerC2SPackets();
		ModItems.registerModItems();

		// ... регистрация команды без изменений ...
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("ask")
					.then(CommandManager.argument("question", StringArgumentType.greedyString())
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player == null) {
									context.getSource().sendError(Text.literal("Эту команду может использовать только игрок."));
									return 0;
								}
								String question = StringArgumentType.getString(context, "question");
								player.sendMessage(Text.literal("§eОтправляем ваш вопрос ИИ..."));
								AiService.getAnswer(player, question);
								return 1;
							})));
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			if (world.getRegistryKey() == World.OVERWORLD) {
				structureNameManager = StructureNameManager.get(world);
				LOGGER.info("StructureNameManager загружен для основного мира.");
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long currentTick = server.getTicks();
			if (structureNameManager == null) return;

			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				UUID uuid = player.getUuid();
				long lastCheck = lastStructureCheckTickForPlayer.getOrDefault(uuid, 0L);

				if (currentTick - lastCheck >= STRUCTURE_CHECK_INTERVAL_TICKS) {
					lastStructureCheckTickForPlayer.put(uuid, currentTick);
					ServerWorld world = player.getServerWorld();

					if (world.getRegistryKey() == World.OVERWORLD) {
						// ИЗМЕНЕНИЕ 3: Выполняем поиск ОДИН РАЗ
						StructureNameManager.StructureCheckResult result = structureNameManager.getOrCreateStructureDataAt(world, player.getBlockPos());

						// Получаем ключ новой позиции из результата
						String newStructurePosKey = result.getPosKey().orElse(null);
						String oldStructurePosKey = lastKnownStructurePosKeyForPlayer.get(uuid);

						// Логика сравнения остается, но теперь она надежна
						if (!Objects.equals(oldStructurePosKey, newStructurePosKey)) {
							// Обновляем последнюю известную позицию
							lastKnownStructurePosKeyForPlayer.put(uuid, newStructurePosKey);

							player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 10, 5));
							player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
							player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));

							// Если мы вошли в новую структуру
							if (newStructurePosKey != null) {
								// Получаем данные из того же результата, что и ключ
								result.getData().ifPresent(data -> {
									if (data.isGenerating.get()) {
										player.sendMessage(Text.literal("§eОбнаружена новая структура: " + data.name), false);
										player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
										player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(data.name)));
									} else {
										player.sendMessage(Text.literal("§aДобро пожаловать в: §l" + data.name + "!"), false);
										player.sendMessage(Text.literal("§a" + data.description), false);
										player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
										player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(data.name)));
										player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(data.description)));
									}
								});
							} else { // Если мы вышли из структуры
								player.sendMessage(Text.literal("§7Вы покинули названную область."), false);
							}
						}
					} else { // Игрок не в основном мире
						if (lastKnownStructurePosKeyForPlayer.containsKey(uuid)) {
							lastKnownStructurePosKeyForPlayer.remove(uuid);
							player.sendMessage(Text.literal("§7Вы сменили измерение и покинули названную область."), false);
							player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 0, 0));
							player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
							player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
						}
					}
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastKnownStructurePosKeyForPlayer.remove(handler.player.getUuid());
			lastStructureCheckTickForPlayer.remove(handler.player.getUuid());
		});

	}

	// ИЗМЕНЕНИЕ 4: Этот метод больше не нужен, его логика перенесена и исправлена
	// @Nullable
	// private String getStructurePosKey(...) { ... }
}