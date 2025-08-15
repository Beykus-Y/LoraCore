// Расположение: src/main/java/com/loracore/computer/VirtualMachineManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.data.*;
import com.loracore.item.ModItems;
import com.loracore.network.SwitchToClientKernelS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.resource.ResourceManager; // <-- Добавь этот импорт
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional; // <-- ИСПРАВЛЕНИЕ: Добавлен импорт
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Серверный синглтон для управления всеми активными экземплярами VirtualMachine.
 * Ключом для доступа к ВМ является уникальный UUID планшета.
 */
public class VirtualMachineManager {

    private static final VirtualMachineManager INSTANCE = new VirtualMachineManager();
    private final Map<UUID, VirtualMachine> runningMachines = new ConcurrentHashMap<>();

    private VirtualMachineManager() {
    }

    public static VirtualMachineManager getInstance() {
        return INSTANCE;
    }

    public VirtualMachine getOrCreate(ServerPlayerEntity player, ItemStack stack) {
        UUID tabletUuid = stack.get(ModComponents.TABLET_UUID);
        if (tabletUuid == null) {
            LoraCoreMod.LOGGER.error("Планшет используется без UUID! Этого не должно происходить.");
            tabletUuid = UUID.randomUUID();
            stack.set(ModComponents.TABLET_UUID, tabletUuid);
        }

        final UUID finalTabletUuid = tabletUuid;
        return runningMachines.computeIfAbsent(finalTabletUuid, key -> {
            LoraCoreMod.LOGGER.info("Создание нового экземпляра ВМ на сервере для планшета {}", key);

            // Создаем новую ВМ
            VirtualMachine newVM = createNewVM(player, stack, finalTabletUuid);
            if (newVM == null) return null;

            // Пытаемся загрузить ее сохраненное состояние
            VirtualMachineState stateManager = VirtualMachineState.get(player.getServerWorld());
            NbtCompound savedNbt = stateManager.loadMachineState(key);
            if (savedNbt != null) {
                LoraCoreMod.LOGGER.info("Найдено сохраненное состояние для ВМ {}", key);
                newVM.readFromNbt(savedNbt);

                // ✅ ВОТ ОНО, КЛЮЧЕВОЕ ИЗМЕНЕНИЕ
                // Если по сохраненным данным ВМ должна быть включена, запускаем ее СРАЗУ ЗДЕСЬ.
                if (newVM.isOn() && !newVM.isMainThreadAlive()) {
                    LoraCoreMod.LOGGER.info("Восстановление рабочего состояния для ВМ {}", key);
                    String biosContent = newVM.getResourceLoader().load("os/bios.lua");
                    if (biosContent != null) {
                        newVM.start(biosContent);
                    } else {
                        LoraCoreMod.LOGGER.error("Критическая ошибка: BIOS не найден при восстановлении ВМ!");
                        newVM.setCrashState("BIOS not found during restore.");
                    }
                }
            }
            return newVM;
        });
    }

    public VirtualMachine get(UUID tabletUuid) {
        return runningMachines.get(tabletUuid);
    }

    public void remove(UUID tabletUuid) {
        if (tabletUuid != null) {
            runningMachines.remove(tabletUuid);
            LoraCoreMod.LOGGER.info("Экземпляр ВМ для планшета {} был удален из менеджера.", tabletUuid);
        }
    }

    private VirtualMachine createNewVM(ServerPlayerEntity player, ItemStack stack, UUID tabletUuid) {
        MotherboardData mobo = stack.get(ModComponents.MOTHERBOARD_DATA);
        if (mobo == null) {
            LoraCoreMod.LOGGER.warn("Tablet {} is missing MotherboardData! Applying default components.", tabletUuid);
            mobo = ModItems.createDefaultMotherboard();
            stack.set(ModComponents.MOTHERBOARD_DATA, mobo);
        }


        String architecture = mobo.cpu().flatMap(s -> Optional.ofNullable(s.get(ModComponents.CPU_DATA)))
                .map(CpuData::architecture).orElse("unknown");

        int totalRamKb = mobo.ram().stream()
                .mapToInt(ramStack -> Optional.ofNullable(ramStack.get(ModComponents.RAM_DATA))
                        .map(RamData::sizeKb).orElse(0))
                .sum();

        // 1. Находим первый установленный жесткий диск
        Optional<ItemStack> hddStackOpt = mobo.storage().stream().findFirst();
        if (hddStackOpt.isEmpty()) {
            LoraCoreMod.LOGGER.error("Tablet {} has no storage device installed!", tabletUuid);
            return null;
        }
        ItemStack hddStack = hddStackOpt.get();

        // 2. Получаем данные с этого диска
        FileSystemsData fsData = hddStack.get(ModComponents.FILE_SYSTEMS_DATA);
        StorageData storageData = hddStack.get(ModComponents.STORAGE_DATA);

        if (fsData == null || storageData == null) {
            LoraCoreMod.LOGGER.error("Storage device in tablet {} is missing required data components!", tabletUuid);
            return null;
        }
        UUID fsUuid = fsData.fsUuid();
        int capacityKb = storageData.capacityKb();

        LoraCoreMod.LOGGER.info("Параметры для новой ВМ: arch={}, ram={}KB, fsUUID={}, tabletUUID={}",
                architecture, totalRamKb, fsUuid, tabletUuid);

        ServerTerminal serverTerminal = new ServerTerminal(tabletUuid);

        // 3. Создаем СИНХРОННУЮ ImageVfs
        IFileSystem imageVfs = new ImageVfs(
                player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT),
                fsUuid,
                capacityKb
        );
        // 4. "Оборачиваем" ее в АСИНХРОННЫЙ ServerVFSWrapper
        IAsyncVFS serverVfs = new ServerVFSWrapper(imageVfs);

        MinecraftServer server = player.getServer();
        ResourceLoader serverResourceLoader = (path) -> {
            String fullPathInJar = "/assets/" + LoraCoreMod.MOD_ID + "/" + path;
            try (var stream = LoraCoreMod.class.getResourceAsStream(fullPathInJar)) {
                if (stream == null) {
                    LoraCoreMod.LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось найти встроенный системный файл: {}", fullPathInJar);
                    return null;
                }
                return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось прочитать встроенный системный файл: {}", fullPathInJar, e);
                return null;
            }
        };

        // Обработчик для Java-ядра пока пустой, так как оно все еще клиентское
        BiConsumer<ServerPlayerEntity, String> javaBootHandler = (p, path) -> {
            LoraCoreMod.LOGGER.info("Запрос на переключение в Java-ядро ({}) для игрока {}", path, p.getName().getString());
            ServerPlayNetworking.send(p, new SwitchToClientKernelS2CPacket(path));
        };

        return new VirtualMachine(player, architecture, totalRamKb, serverTerminal,
                serverResourceLoader, serverVfs, fsUuid, tabletUuid,
                javaBootHandler);
    }
    public Map<UUID, VirtualMachine> getRunningMachines() {
        return this.runningMachines;
    }
}