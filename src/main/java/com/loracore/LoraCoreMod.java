// Полный исправленный файл: src/main/java/com/loracore/LoraCoreMod.java
package com.loracore;

import com.loracore.component.ModComponents;
import com.loracore.computer.*;
import com.loracore.item.ModItems;
import com.loracore.item.TabletItem;
import com.loracore.network.ModNetworking;
import com.loracore.network.SystemMetricsS2CPacket;
import com.loracore.network.graphics.ScreenUpdateS2CPacket;
import com.loracore.util.PromptManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class LoraCoreMod implements ModInitializer {
	public static final String MOD_ID = "loracore";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, String> lastKnownStructurePosKeyForPlayer = new HashMap<>();
	private static final Map<UUID, Long> lastStructureCheckTickForPlayer = new HashMap<>();
	private static final int STRUCTURE_CHECK_INTERVAL_TICKS = 60;
	private static StructureNameManager structureNameManager;

	@Override
	public void onInitialize() {
		LOGGER.info("Загрузка мода LoraCore...");
		ConfigManager.loadConfig();
		PromptManager.register();
		VirtualFileSystemManager.registerResourceManagerListener();
		ModNetworking.registerC2SPackets();
		ModItems.registerModItems();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			VirtualFileSystemManager.getInstance().initialize(server);
			ImageVfsManager.getInstance().initialize(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Вызываем tick() для всех активных VirtualMachine
			VirtualMachineManager vmManager = VirtualMachineManager.getInstance();
			for (VirtualMachine vm : vmManager.getRunningMachines().values()) {
				if (vm != null && vm.isOn()) {
					vm.tick();
				}
			}

			// Отправляем метрики каждые 20 тиков (1 секунда)
			long currentTick = server.getTicks();
			boolean shouldSendMetrics = (currentTick % 20 == 0);

			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				ItemStack mainHandStack = player.getMainHandStack();
				if (mainHandStack.getItem() instanceof TabletItem) {
					updateTabletScreen(player, mainHandStack);
					
					// Отправляем метрики для планшета в главной руке
					if (shouldSendMetrics) {
						sendMetricsIfNeeded(player, mainHandStack, vmManager);
					}
				}

				for (ItemStack stack : player.getInventory().main) {
					if (stack.getItem() instanceof TabletItem && stack != mainHandStack) {
						ServerScreenState screen = TabletScreenManager.getInstance().getOrCreateScreen(stack);
						if (screen.isDirty()) {
							updateTabletScreen(player, stack);
						}
						
						// Отправляем метрики для всех планшетов в инвентаре
						if (shouldSendMetrics) {
							sendMetricsIfNeeded(player, stack, vmManager);
						}
					}
				}
			}
		});

		ServerWorldEvents.LOAD.register((server, world) -> {
			if (world.getRegistryKey() == World.OVERWORLD) {
				structureNameManager = StructureNameManager.get(world);
				// ИЗМЕНЕНИЕ: Убираем лишний лог
				LOGGER.debug("StructureNameManager загружен для основного мира.");
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
							player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 10, 5));
							player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
							player.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));

							if (newStructurePosKey != null) {
								result.getData().ifPresent(data -> {
									if (data.isGenerating.get()) {
										player.sendMessage(Text.translatable("structure.loracore.discover.generating", data.name).formatted(Formatting.YELLOW), false);
									} else {
										Text structureName = Text.literal(data.name);
										Text structureDescription = Text.literal(data.description);
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

		// --- ДОБАВЛЕНО: Сохранение состояния всех ВМ при остановке сервера ---
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Сервер останавливается. Сохранение состояний всех виртуальных машин...");
			VirtualMachineState stateManager = VirtualMachineState.get(server.getOverworld());
			VirtualMachineManager vmManager = VirtualMachineManager.getInstance();

			vmManager.getRunningMachines().forEach((uuid, vm) -> {
				NbtCompound vmNbt = vm.writeToNbt();
				stateManager.saveMachineState(uuid, vmNbt);
				vm.shutdown(); // Корректно выключаем ВМ
			});
			LOGGER.info("Состояния всех ВМ сохранены.");
		});
	}

	private static void updateTabletScreen(ServerPlayerEntity player, ItemStack stack) {
		try {
			ServerScreenState screen = TabletScreenManager.getInstance().getOrCreateScreen(stack);
			if (screen.isDirty() && screen.getPixelBuffer() != null) {
				UUID tabletUuid = stack.get(ModComponents.TABLET_UUID);
				if (tabletUuid != null) {
					ServerPlayNetworking.send(player, new ScreenUpdateS2CPacket(
							tabletUuid,
							screen.getPixelBuffer()
					));
					screen.clearDirtyFlag();
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error updating tablet screen for player {}: {}", player.getName().getString(), e.getMessage());
		}
	}

	private static void sendMetricsIfNeeded(ServerPlayerEntity player, ItemStack stack, VirtualMachineManager vmManager) {
		try {
			UUID tabletUuid = stack.get(ModComponents.TABLET_UUID);
			if (tabletUuid == null) return;

			VirtualMachine vm = vmManager.get(tabletUuid);
			if (vm == null || !vm.isOn()) return;

			VirtualMachine.SystemMetrics metrics = vm.getMetricsForClient();
			if (metrics == null) return; // Метрики еще не готовы

			ServerPlayNetworking.send(player, new SystemMetricsS2CPacket(
					tabletUuid,
					metrics.cpuLoad(),
					metrics.ramUsedKb(),
					metrics.ramTotalKb(),
					metrics.diskQueue(),
					metrics.tabletUuidStr(),
					metrics.fsUuidStr()
			));
		} catch (Exception e) {
			LOGGER.error("Error sending metrics for tablet {}: {}", stack.get(ModComponents.TABLET_UUID), e.getMessage());
		}
	}
}