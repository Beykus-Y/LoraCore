package com.loracore.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Хранит характеристики графического процессора (GPU).
 * Эти данные прикрепляются к предмету-видеокарте.
 */
public record GpuData(String tier, int vramKb) {
    public static final Codec<GpuData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("tier").forGetter(GpuData::tier),
                    Codec.INT.fieldOf("vram_kb").forGetter(GpuData::vramKb)
            ).apply(instance, GpuData::new)
    );
}