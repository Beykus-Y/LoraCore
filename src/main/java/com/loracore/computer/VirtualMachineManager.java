// Расположение: src/main/java/com/loracore/computer/VirtualMachineManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.data.CpuData;
import com.loracore.component.data.FileSystemsData;
import com.loracore.component.data.MotherboardData;
import com.loracore.component.data.RamData;
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
        if (mobo == null) return null;

        String architecture = mobo.cpu().flatMap(s -> Optional.ofNullable(s.get(ModComponents.CPU_DATA)))
                .map(CpuData::architecture).orElse("unknown");

        int totalRamKb = mobo.ram().stream()
                .mapToInt(ramStack -> Optional.ofNullable(ramStack.get(ModComponents.RAM_DATA))
                        .map(RamData::sizeKb).orElse(0))
                .sum();

        FileSystemsData oldFsData = stack.get(ModComponents.FILE_SYSTEMS_DATA);

        // Создаем новую, ГАРАНТИРОВАННО изменяемую карту.
        // Если старые данные есть, копируем их.
        Map<String, UUID> mutableUuids = new HashMap<>();
        if (oldFsData != null) {
            mutableUuids.putAll(oldFsData.uuids());
        }

        // Теперь безопасно используем computeIfAbsent на нашей новой карте.
        UUID fsUuid = mutableUuids.computeIfAbsent("0", k -> UUID.randomUUID());

        // Создаем новый объект данных с обновленной картой и записываем его обратно в ItemStack.
        FileSystemsData newFsData = new FileSystemsData(mutableUuids);
        stack.set(ModComponents.FILE_SYSTEMS_DATA, newFsData);

        LoraCoreMod.LOGGER.info("Параметры для новой ВМ: arch={}, ram={}KB, fsUUID={}, tabletUUID={}",
                architecture, totalRamKb, fsUuid, tabletUuid);

        // --- ИЗМЕНЕНИЕ: Создаем ВМ с серверными компонентами ---
        LoraCoreMod.LOGGER.info("Параметры для новой ВМ: arch={}, ram={}KB, fsUUID={}, tabletUUID={}",
                architecture, totalRamKb, fsUuid, tabletUuid);

        // --- ИЗМЕНЕНИЕ: Создаем ВМ с РЕАЛЬНЫМИ серверными компонентами ---
        ServerTerminal serverTerminal = new ServerTerminal(tabletUuid);
        IAsyncVFS serverVfs = new ServerVFSWrapper(fsUuid);

        // Создаем ResourceLoader на лету, используя серверный ResourceManager
        MinecraftServer server = player.getServer();
        ResourceManager resourceManager = server.getResourceManager();
        ResourceLoader serverResourceLoader = (path) -> {
            // Этот путь формируется от корня JAR-файла.
            // Наши ресурсы лежат в /assets/loracore/
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