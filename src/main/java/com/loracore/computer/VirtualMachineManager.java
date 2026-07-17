// Расположение: src/main/java/com/loracore/computer/VirtualMachineManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.data.*;
import com.loracore.computer.device.ConfigurationSpaceDevice;
import com.loracore.computer.device.KeyboardDevice;
import com.loracore.computer.device.WorldBridgeDevice;
import com.loracore.computer.pnp.PnpEntry;
import com.loracore.item.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.resource.ResourceManager; // <-- Добавь этот импорт
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.*;
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
                // В режиме серверной VM без Lua BIOS автоматический запуск не выполняется
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
        // --- 1. Извлечение компонентов из NBT ---
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

        // Проверка наличия диска
        Optional<ItemStack> hddStackOpt = mobo.storage().stream().findFirst();
        if (hddStackOpt.isEmpty()) {
            LoraCoreMod.LOGGER.error("Tablet {} has no storage device installed!", tabletUuid);
            return null;
        }
        ItemStack hddStack = hddStackOpt.get();

        FileSystemsData fsData = hddStack.get(ModComponents.FILE_SYSTEMS_DATA);
        StorageData storageData = hddStack.get(ModComponents.STORAGE_DATA);

        if (fsData == null || storageData == null) {
            LoraCoreMod.LOGGER.error("Storage device in tablet {} is missing required data components!", tabletUuid);
            return null;
        }
        UUID fsUuid = fsData.fsUuid();
        int capacityKb = storageData.capacityKb();

        LoraCoreMod.LOGGER.info("VM Init: arch={}, ram={}KB, fsUUID={}, tabletUUID={}",
                architecture, totalRamKb, fsUuid, tabletUuid);

        // --- 2. Инициализация VFS ---
        IFileSystem imageVfs = new ImageVfs(
                player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT),
                fsUuid,
                capacityKb
        );

        // --- 3. Инициализация Шины и Устройств ---
        SystemBus systemBus = new SystemBus();
        List<PnpEntry> pnpTable = new ArrayList<>();
        final int CONFIG_ROM_ADDR = 0xFFF000;

        try {
            // == A. RAM (0x000000 - ...) ==
            // Всегда начинается с 0
            int ramSizeBytes = totalRamKb * 1024;
            GenericRam systemRam = new GenericRam(ramSizeBytes);
            systemBus.mapDevice(0x000000, systemRam);
            pnpTable.add(new PnpEntry(PnpEntry.TYPE_RAM, 0x000000, ramSizeBytes, 0));

            // Текущий адрес после RAM (выравненный до 4KB)
            int currentAddr = (ramSizeBytes + 0xFFF) & ~0xFFF;

            // == B. Disk Controller (FIXED: 0x300000) ==
            // Хардкод адреса обязателен для совместимости с текущим ядром (consts.lc)
            // Если RAM > 3MB, это вызовет ошибку (но у нас пока макс 2MB)
            int diskAddr = 0x300000;
            if (currentAddr > diskAddr) {
                LoraCoreMod.LOGGER.error("VM Init Error: RAM too large ({} bytes), overlaps Disk Controller at 0x300000", ramSizeBytes);
                // Fallback: обрезаем RAM (виртуально) или просто предупреждаем
                // Для стабильности лучше вернуть null
                return null;
            }

            // Подготовка файла диска
            Path diskFolder = player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                    .resolve("loracore_vfs")
                    .resolve(fsUuid.toString());
            String rawDiskFilename = "disk.bin";
            int diskSizeBytes = capacityKb * 1024;

            RawDiskDrive rawDrive = new RawDiskDrive(diskFolder, rawDiskFilename, diskSizeBytes);
            initializeDiskImageIfNeeded(rawDrive, diskFolder.resolve(rawDiskFilename),
                    diskSizeBytes, fsUuid);

            com.loracore.computer.device.DiskControllerDevice diskController =
                    new com.loracore.computer.device.DiskControllerDevice(systemBus, rawDrive);

            systemBus.mapDevice(diskAddr, diskController);
            pnpTable.add(new PnpEntry(PnpEntry.TYPE_STORAGE, diskAddr, diskController.getSize(), 0));
            LoraCoreMod.LOGGER.info("PnP: Disk Controller Mapped at 0x300000");

            // == C. Keyboard (FIXED: 0x310000) ==
            // Также фиксированный адрес
            int keybAddr = 0x310000;
            com.loracore.computer.device.KeyboardDevice keyboard = new com.loracore.computer.device.KeyboardDevice(systemBus);
            systemBus.mapDevice(keybAddr, keyboard);
            pnpTable.add(new PnpEntry(PnpEntry.TYPE_INPUT, keybAddr, keyboard.getSize(), 0));
            LoraCoreMod.LOGGER.info("PnP: Keyboard Mapped at 0x310000");

            // == D. Read-only survival world bridge (0x320000) ==
            int worldBridgeAddr = 0x320000;
            WorldBridgeDevice worldBridge = new WorldBridgeDevice(player);
            systemBus.mapDevice(worldBridgeAddr, worldBridge);
            pnpTable.add(new PnpEntry(PnpEntry.TYPE_WORLD, worldBridgeAddr, worldBridge.getSize(), 0));
            LoraCoreMod.LOGGER.info("PnP: World Bridge Mapped at 0x320000 (read-only ABI v1)");

            // == E. GPU (DYNAMIC START: 0x400000) ==
            // Начинаем с 4MB, чтобы гарантированно не задеть диск и клавиатуру
            int gpuAddr = 0x400000;

            ServerScreenState screenState = TabletScreenManager.getInstance().getOrCreateScreen(stack);
            GpuMmioDevice.GpuTier gpuTier = GpuMmioDevice.GpuTier.TIER3;
            if (mobo.gpu().isPresent()) {
                gpuTier = GpuMmioDevice.GpuTier.TIER3;
            }

            GpuMmioDevice gpuMmioDevice = new GpuMmioDevice(screenState, gpuTier);
            int gpuSize = gpuMmioDevice.getSize();

            // Проверка на выход за пределы памяти (до PnP ROM)
            if (gpuAddr + gpuSize > CONFIG_ROM_ADDR) {
                LoraCoreMod.LOGGER.error("Critical Error: GPU (size {}) at 0x{} does not fit before PnP ROM at 0x{}",
                        gpuSize, Integer.toHexString(gpuAddr), Integer.toHexString(CONFIG_ROM_ADDR));
                return null;
            }

            systemBus.mapDevice(gpuAddr, gpuMmioDevice);
            pnpTable.add(new PnpEntry(PnpEntry.TYPE_GPU, gpuAddr, gpuSize, 0));
            LoraCoreMod.LOGGER.info("PnP: GPU Mapped at 0x{}", Integer.toHexString(gpuAddr));

            // == F. PnP Configuration Space (FIXED: 0xFFF000) ==
            ConfigurationSpaceDevice configDevice = new ConfigurationSpaceDevice(pnpTable);
            systemBus.mapDevice(CONFIG_ROM_ADDR, configDevice);

            // --- 4. Сборка остальной периферии ---
            ServerTerminal serverTerminal = new ServerTerminal(tabletUuid);
            IAsyncVFS serverVfs = new ServerVFSWrapper(imageVfs);

            ResourceLoader serverResourceLoader = (path) -> {
                String fullPathInJar = "/assets/" + LoraCoreMod.MOD_ID + "/" + path;
                try (var stream = LoraCoreMod.class.getResourceAsStream(fullPathInJar)) {
                    if (stream == null) {
                        LoraCoreMod.LOGGER.error("CRITICAL: System file missing: {}", fullPathInJar);
                        return null;
                    }
                    return stream.readAllBytes();
                } catch (Exception e) {
                    LoraCoreMod.LOGGER.error("CRITICAL: Failed to read system file: {}", fullPathInJar, e);
                    return null;
                }
            };

            // Возврат готовой VM
            return new VirtualMachine(player, architecture, totalRamKb, serverTerminal,
                    serverResourceLoader, serverVfs, fsUuid, tabletUuid,
                    systemBus, systemRam, gpuMmioDevice, keyboard);

        } catch (Exception e) {
            LoraCoreMod.LOGGER.error("Fatal error during VM creation for tablet {}", tabletUuid, e);
            return null;
        }
    }

    /**
     * Вспомогательный метод для инициализации файла диска
     */
    private void initializeDiskImageIfNeeded(RawDiskDrive rawDrive, Path diskPath,
                                             int diskSizeBytes, UUID fsUuid) throws java.io.IOException {
        try (var stream = LoraCoreMod.class.getResourceAsStream(
                "/assets/" + LoraCoreMod.MOD_ID + "/os/disk.bin")) {
            if (stream == null) {
                throw new java.io.IOException("Bundled LoraOS factory image is missing");
            }
            FactoryDiskInstaller.Result result = FactoryDiskInstaller.ensureInstalled(
                    rawDrive, diskPath, diskSizeBytes, stream.readAllBytes());
            switch (result) {
                case CREATED -> LoraCoreMod.LOGGER.info(
                        "[VM] Flashed LoraCore 2.x factory image to new disk {}", fsUuid);
                case MIGRATED -> LoraCoreMod.LOGGER.warn(
                        "[VM] Migrated legacy disk {} to 2.x; backup saved as disk.bin.pre-2.0.bak",
                        fsUuid);
                case CURRENT -> { }
            }
        }
    }
    public Map<UUID, VirtualMachine> getRunningMachines() {
        return this.runningMachines;
    }
}
