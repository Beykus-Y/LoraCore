package com.loracore.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Хранит характеристики устройства хранения данных (например, жесткого диска).
 * Этот компонент прикрепляется к предметам-накопителям.
 *
 * @param capacityKb Максимальная ёмкость накопителя в килобайтах.
 */
public record StorageData(int capacityKb) {
    public static final Codec<StorageData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("capacity_kb").forGetter(StorageData::capacityKb)
            ).apply(instance, StorageData::new)
    );
}