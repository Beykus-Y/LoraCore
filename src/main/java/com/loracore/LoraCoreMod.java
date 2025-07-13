package com.loracore;

import com.loracore.item.ModItems;
import com.loracore.network.ModNetworking;
import com.loracore.util.PromptManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class LoraCoreMod implements ModInitializer {
	public static final String MOD_ID = "loracore";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, String> lastKnownStructurePosKeyForPlayer = new HashMap<>();
	private static final Map<UUID, Long> lastStructureCheckTickForPlayer = new HashMap<>();
	private static final int STRUCTURE_CHECK_INTERVAL_TICKS = 60; // Проверка раз в 3 секунды
	private static StructureNameManager structureNameManager;

	@Override
	public void onInitialize() {
		LOGGER.info("Загрузка мода AI Mod...");
		ConfigManager.loadConfig();
		PromptManager.register();
		ModNetworking.registerC2SPackets();
		ModItems.registerModItems();

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
						StructureNameManager.StructureCheckResult result = structureNameManager.getOrCreateStructureDataAt(world, player);

						String newStructurePosKey = result.getPosKey().orElse(null);
						String oldStructurePosKey = lastKnownStructurePosKeyForPlayer.get(uuid);

						if (!Objects.equals(oldStructurePosKey, newStructurePosKey)) {
							lastKnownStructurePosKeyForPlayer.put(uuid, newStructurePosKey);

							// Сначала очищаем старый заголовок
							player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 10, 5));
							player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
							player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));

							if (newStructurePosKey != null) {
								result.getData().ifPresent(data -> {
									// ИЗМЕНЕННАЯ ЛОГИКА:
									// Четко разделяем, когда показывать сообщение о генерации, а когда - результат.
									if (data.isGenerating.get()) {
										// Показываем сообщение о новой структуре ТОЛЬКО один раз, когда начинается генерация.
										// Название "Неизвестная структура..." берется из StructureNameManager
										player.sendMessage(Text.translatable("structure.loracore.discover.generating", data.name).formatted(Formatting.YELLOW), false);
									} else {
										// Когда генерация завершена (isGenerating == false), показываем полный результат.
										Text structureName = Text.literal(data.name);
										Text structureDescription = Text.literal(data.description);

										// Используем ключи для локализации обертки
										player.sendMessage(Text.translatable("structure.loracore.enter.title", structureName.copy().formatted(Formatting.BOLD)).formatted(Formatting.GREEN), false);
										player.sendMessage(Text.translatable("structure.loracore.enter.description", structureDescription).formatted(Formatting.GREEN), false);

										player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
										player.networkHandler.sendPacket(new TitleS2CPacket(structureName));
										player.networkHandler.sendPacket(new SubtitleS2CPacket(structureDescription));
									}
								});
							} else {
								player.sendMessage(Text.translatable("structure.loracore.leave.area").formatted(Formatting.GRAY), false);
							}
						}
					} else {
						if (lastKnownStructurePosKeyForPlayer.containsKey(uuid)) {
							lastKnownStructurePosKeyForPlayer.remove(uuid);
							player.sendMessage(Text.translatable("structure.loracore.leave.dimension").formatted(Formatting.GRAY), false);
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
}