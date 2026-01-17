// Файл: src/main/java/com/loracore/item/ModItems.java

package com.loracore.item;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.data.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ModItems {
    // --- Компоненты ---
    public static final Item CPU_T1 = registerItem("cpu_t1",
            new CpuItem(new Item.Settings().component(ModComponents.CPU_DATA, new CpuData("lora_mobile_v1", 25))),
            ItemGroups.INGREDIENTS);

    public static final Item CPU_T2 = registerItem("cpu_t2",
            new CpuItem(new Item.Settings().component(ModComponents.CPU_DATA, new CpuData("tier2", 50))),
            ItemGroups.INGREDIENTS);

    public static final Item CPU_T3 = registerItem("cpu_t3",
            new CpuItem(new Item.Settings().component(ModComponents.CPU_DATA, new CpuData("tier3", 75))),
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T1 = registerItem("ram_t1",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(512))),
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T2 = registerItem("ram_t2",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(1024))),
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T3 = registerItem("ram_t3",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(2048))),
            ItemGroups.INGREDIENTS);

    // ИСПРАВЛЕННАЯ РЕГИСТРАЦИЯ ЖЕСТКОГО ДИСКА
    public static final Item HDD_T1 = registerItem("hdd_t1",
            new HardDriveItem(new Item.Settings()
                    .component(ModComponents.STORAGE_DATA, new StorageData(1024)) // 1 MB
                    .component(ModComponents.FILE_SYSTEMS_DATA, new FileSystemsData(UUID.randomUUID()))),
            ItemGroups.INGREDIENTS);

    public static final Item FIRMWARE_ROM = registerItem("firmware_rom",
            new Item(new Item.Settings().component(ModComponents.FIRMWARE_DATA, new FirmwareData(new Identifier(LoraCoreMod.MOD_ID, "os/recovery.lua")))),
            ItemGroups.INGREDIENTS);

    // --- Готовое устройство ---

    // ИСПРАВЛЕННАЯ РЕГИСТРАЦИЯ ПЛАНШЕТА
    public static final Item TABLET = registerItem("tablet",
            new TabletItem(new Item.Settings()
                    .rarity(Rarity.UNCOMMON)
                    .maxCount(1)
                    .fireproof()
                    // Компонент MotherboardData остается, он описывает "слоты"

                    // КОМПОНЕНТ FILE_SYSTEMS_DATA ОТСЮДА УДАЛЕН, ТАК КАК ОН ПРИНАДЛЕЖИТ ДИСКУ
            ),
            ItemGroups.TOOLS);


    public static MotherboardData createDefaultMotherboard() {
        ItemStack cpu = new ItemStack(CPU_T1);
        ItemStack ram = new ItemStack(RAM_T1);
        ItemStack hdd = new ItemStack(HDD_T1); // HDD создается здесь и помещается в слот
        ItemStack firmware = new ItemStack(FIRMWARE_ROM);

        hdd.set(ModComponents.FILE_SYSTEMS_DATA, new FileSystemsData(UUID.randomUUID()));

        return new MotherboardData(
                Optional.of(cpu),
                Optional.empty(),
                List.of(ram),
                List.of(hdd), // <-- hdd со своим FileSystemsData теперь находится внутри MotherboardData
                Optional.of(firmware)
        );
    }

    private static Item registerItem(String name, Item item, RegistryKey<ItemGroup> group) {
        Identifier id = new Identifier(LoraCoreMod.MOD_ID, name);
        Item registeredItem = Registry.register(Registries.ITEM, id, item);
        ItemGroupEvents.modifyEntriesEvent(group).register(entries -> entries.add(registeredItem));
        return registeredItem;
    }

    public static void registerModItems() {
        LoraCoreMod.LOGGER.info("Регистрация предметов для мода " + LoraCoreMod.MOD_ID);
    }
}