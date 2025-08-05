package com.loracore.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;
import java.util.Optional;

/**
 * Хранит конфигурацию "материнской платы" планшета, то есть
 * установленные в нее компоненты (CPU, RAM и т.д.).
 * Этот компонент прикрепляется к главному предмету - планшету.
 */
public record MotherboardData(
        Optional<ItemStack> cpu,
        Optional<ItemStack> gpu,
        List<ItemStack> ram,
        List<ItemStack> storage,
        Optional<ItemStack> firmware
) {

    /**
     * Основной Codec для сохранения/загрузки MotherboardData на диск.
     */
    public static final Codec<MotherboardData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ItemStack.CODEC.optionalFieldOf("cpu").forGetter(MotherboardData::cpu),
                    ItemStack.CODEC.optionalFieldOf("gpu").forGetter(MotherboardData::gpu),
                    ItemStack.CODEC.listOf().optionalFieldOf("ram", List.of()).forGetter(MotherboardData::ram),
                    ItemStack.CODEC.listOf().optionalFieldOf("storage", List.of()).forGetter(MotherboardData::storage),
                    ItemStack.CODEC.optionalFieldOf("firmware").forGetter(MotherboardData::firmware)
            ).apply(instance, MotherboardData::new)
    );

    /**
     * [ИСПРАВЛЕНО]
     * PacketCodec для отправки MotherboardData по сети.
     * Использует правильные кодеки для Optional и List полей.
     */
    public static final PacketCodec<RegistryByteBuf, MotherboardData> PACKET_CODEC = PacketCodec.tuple(
            // Для Optional<ItemStack> используем PacketCodecs.optional()
            PacketCodecs.optional(ItemStack.PACKET_CODEC), MotherboardData::cpu,
            PacketCodecs.optional(ItemStack.PACKET_CODEC), MotherboardData::gpu,

            // Для List<ItemStack> используем ItemStack.LIST_PACKET_CODEC
            ItemStack.LIST_PACKET_CODEC, MotherboardData::ram,
            ItemStack.LIST_PACKET_CODEC, MotherboardData::storage,

            // Для Optional<ItemStack> используем PacketCodecs.optional()
            PacketCodecs.optional(ItemStack.PACKET_CODEC), MotherboardData::firmware,

            // Конструктор
            MotherboardData::new
    );
}