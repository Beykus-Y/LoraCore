package com.loracore.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Хранит характеристики центрального процессора (CPU).
 * Эти данные прикрепляются к предмету-процессору.
 */
public record CpuData(String architecture, int frequencyMhz) {
    public static final Codec<CpuData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("architecture").forGetter(CpuData::architecture),
                    Codec.INT.fieldOf("frequency_mhz").forGetter(CpuData::frequencyMhz)
            ).apply(instance, CpuData::new)
    );
}