// [ИЗМЕНЕНО]
package com.loracore.item;

import com.loracore.LoraCoreMod;
import com.loracore.component.ModComponents;
import com.loracore.component.ModComponents.*;
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
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ModItems {
    // --- Компоненты ---
    public static final Item CPU_T1 = registerItem("cpu_t1",
            new CpuItem(new Item.Settings().component(ModComponents.CPU_DATA, new CpuData("lora_mobile_v1", 25))),
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T1 = registerItem("ram_t1",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(512))), // 512 KB
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T2 = registerItem("ram_t2",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(1024))), // 1024 KB
            ItemGroups.INGREDIENTS);

    public static final Item RAM_T3 = registerItem("ram_t3",
            new RamItem(new Item.Settings().component(ModComponents.RAM_DATA, new RamData(2048))), // 2048 KB
            ItemGroups.INGREDIENTS);

    public static final Item HDD_T1 = registerItem("hdd_t1",
            new HardDriveItem(new Item.Settings().component(ModComponents.STORAGE_DATA, new StorageData(1024))), // 1 MB
            ItemGroups.INGREDIENTS);

    public static final Item FIRMWARE_ROM = registerItem("firmware_rom",
            new Item(new Item.Settings().component(ModComponents.FIRMWARE_DATA, new FirmwareData(new Identifier(LoraCoreMod.MOD_ID, "os/recovery.lua")))),
            ItemGroups.INGREDIENTS);

    // --- Готовое устройство ---
    public static final Item TABLET = registerItem("tablet",
            new TabletItem(new Item.Settings()
                    .rarity(Rarity.UNCOMMON)
                    .maxCount(1)
                    .fireproof()
                    // [ИЗМЕНЕНО] Добавляем новый компонент с пустой картой UUID
                    .component(ModComponents.MOTHERBOARD_DATA, createDefaultMotherboard())
                    .component(ModComponents.FILE_SYSTEMS_DATA, new FileSystemsData(Map.of()))
            ),
            ItemGroups.TOOLS);


    /**
     * Вспомогательный метод для создания "материнской платы" по умолчанию для планшета.
     */
    private static MotherboardData createDefaultMotherboard() {
        // Создаем виртуальные ItemStack'и компонентов, которые будут "внутри" планшета
        ItemStack cpu = new ItemStack(CPU_T1);
        ItemStack ram = new ItemStack(RAM_T1);
        ItemStack hdd = new ItemStack(HDD_T1);
        // Теперь нам НЕ НУЖНО модифицировать hdd, он создается "чистым"
        ItemStack firmware = new ItemStack(FIRMWARE_ROM);

        return new MotherboardData(
                Optional.of(cpu),
                Optional.empty(),
                List.of(ram),
                List.of(hdd),
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