// Расположение: src/main/java/com/loracore/computer/VirtualMachineManager.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.data.*;
import com.loracore.computer.device.ConfigurationSpaceDevice;
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

        // === 3. Создаем VFS заранее (нужна для контроллера диска) ===
        // Создаем СИНХРОННУЮ ImageVfs
        IFileSystem imageVfs = new ImageVfs(
                player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT),
                fsUuid,
                capacityKb
        );

        // === HARDWARE INITIALIZATION & PnP MAPPING ===

        SystemBus systemBus = new SystemBus();
        List<PnpEntry> pnpTable = new ArrayList<>();

        // Адрес начала конфигурационного пространства (фиксирован: последние 4КБ)
        final int CONFIG_ROM_ADDR = 0xFFF000;
        int currentAddress = 0;

        // --- 4. RAM Allocation (Always starts at 0x000000) ---
        int ramSizeBytes = totalRamKb * 1024;
        GenericRam systemRam = new GenericRam(ramSizeBytes);

        systemBus.mapDevice(currentAddress, systemRam);
        pnpTable.add(new PnpEntry(PnpEntry.TYPE_RAM, currentAddress, ramSizeBytes, 0));

        currentAddress += ramSizeBytes;
        // Выравнивание адреса до 4KB (0x1000)
        currentAddress = (currentAddress + 0xFFF) & ~0xFFF;

        // --- 5. GPU Allocation ---
        ServerScreenState screenState = TabletScreenManager.getInstance().getOrCreateScreen(stack);

        // Логика определения Tier GPU
        GpuMmioDevice.GpuTier gpuTier = GpuMmioDevice.GpuTier.TIER1;
        if (mobo.gpu().isPresent()) {
            // Здесь можно добавить логику чтения тира из GpuData
            gpuTier = GpuMmioDevice.GpuTier.TIER3; // Пока ставим Tier 3 по умолчанию, если карта есть
        }

        GpuMmioDevice gpuMmioDevice = new GpuMmioDevice(screenState, gpuTier);
        int gpuSize = gpuMmioDevice.getSize();

        // Проверка на переполнение памяти
        if (currentAddress + gpuSize > CONFIG_ROM_ADDR) {
            LoraCoreMod.LOGGER.error("Critical Error: GPU does not fit in memory space!");
            return null;
        }

        systemBus.mapDevice(currentAddress, gpuMmioDevice);
        pnpTable.add(new PnpEntry(PnpEntry.TYPE_GPU, currentAddress, gpuSize, 0));

        LoraCoreMod.LOGGER.info("PnP: GPU Mapped at 0x{}", String.format("%06X", currentAddress));

        currentAddress += gpuSize;
        currentAddress = (currentAddress + 0xFFF) & ~0xFFF; // Align

        // --- 6. Storage (HDD Controller) Allocation ---

        // Подготовка "сырого" файла диска внутри VFS
        Path diskFolder = player.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                .resolve("loracore_vfs")
                .resolve(fsUuid.toString());

        String rawDiskFilename = "disk.bin";
        int diskSizeBytes = capacityKb * 1024;

        // Создаем физический привод
        RawDiskDrive rawDrive = new RawDiskDrive(diskFolder, rawDiskFilename, diskSizeBytes);

        try {
            // Если файла диска еще нет на физическом носителе (HDD/SSD хоста)
            java.io.File diskFile = diskFolder.resolve(rawDiskFilename).toFile();
            if (!diskFile.exists()) {
                rawDrive.ensureExists(); // Создает пустой файл нужного размера

                // Читаем дефолтный загрузочный сектор из ресурсов мода
                byte[] defaultDiskContent = null;
                try (var stream = LoraCoreMod.class.getResourceAsStream("/assets/" + LoraCoreMod.MOD_ID + "/os/disk.bin")) {
                    if (stream != null) {
                        defaultDiskContent = stream.readAllBytes();
                    }
                }

                // Если нашли дефолтный образ - записываем его в начало диска
                if (defaultDiskContent != null) {
                    // Записываем данные напрямую в файл, так как мы сейчас в синхронном потоке инициализации
                    java.nio.file.Files.write(diskFile.toPath(), defaultDiskContent, java.nio.file.StandardOpenOption.WRITE);
                    LoraCoreMod.LOGGER.info("[VM] Flashed default boot image to new disk {}", fsUuid);
                }
            }
        } catch (java.io.IOException e) {
            LoraCoreMod.LOGGER.error("Critical: Failed to initialize raw disk file for VM", e);
            return null;
        }

        // Создаем контроллер, передавая ему нашу шину и новый RawDrive
        com.loracore.computer.device.DiskControllerDevice diskController =
                new com.loracore.computer.device.DiskControllerDevice(systemBus, rawDrive);

        int diskCtrlSize = diskController.getSize();

        if (currentAddress + diskCtrlSize > CONFIG_ROM_ADDR) {
            LoraCoreMod.LOGGER.error("Critical Error: Disk Controller does not fit in memory space.");
            return null;
        }

        systemBus.mapDevice(currentAddress, diskController);
        pnpTable.add(new PnpEntry(PnpEntry.TYPE_STORAGE, currentAddress, diskCtrlSize, 0));

        LoraCoreMod.LOGGER.info("PnP: Disk Controller Mapped at 0x{}", String.format("%06X", currentAddress));

        currentAddress += diskCtrlSize;
        currentAddress = (currentAddress + 0xFFF) & ~0xFFF;



        // --- 7. Configuration Space (ROM) ---
        // Создаем ROM с таблицей устройств
        ConfigurationSpaceDevice configDevice = new ConfigurationSpaceDevice(pnpTable);
        systemBus.mapDevice(CONFIG_ROM_ADDR, configDevice);

        // ==========================================

        ServerTerminal serverTerminal = new ServerTerminal(tabletUuid);

        // Оборачиваем VFS в асинхронную обертку для использования в API (если нужно)
        IAsyncVFS serverVfs = new ServerVFSWrapper(imageVfs);

        MinecraftServer server = player.getServer();
        ResourceLoader serverResourceLoader = (path) -> {
            String fullPathInJar = "/assets/" + LoraCoreMod.MOD_ID + "/" + path;
            try (var stream = LoraCoreMod.class.getResourceAsStream(fullPathInJar)) {
                if (stream == null) {
                    LoraCoreMod.LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось найти файл: {}", fullPathInJar);
                    return null;
                }
                return stream.readAllBytes();
            } catch (Exception e) {
                LoraCoreMod.LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось прочитать встроенный системный файл: {}", fullPathInJar, e);
                return null;
            }
        };

        // ВАЖНО: Передаем уже настроенные компоненты в конструктор VM
        // Обратите внимание: DiskControllerDevice живет в SystemBus, поэтому передавать его отдельно не обязательно,
        // но он доступен CPU через MMIO.
        return new VirtualMachine(player, architecture, totalRamKb, serverTerminal,
                serverResourceLoader, serverVfs, fsUuid, tabletUuid,
                systemBus, systemRam, gpuMmioDevice);
    }
    public Map<UUID, VirtualMachine> getRunningMachines() {
        return this.runningMachines;
    }
}
