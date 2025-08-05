package com.loracore.component.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Хранит характеристики оперативной памяти (RAM).
 * Эти данные прикрепляются к предмету-планке памяти.
 */
public record RamData(int sizeKb) {
    public static final Codec<RamData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("size_kb").forGetter(RamData::sizeKb)
            ).apply(instance, RamData::new)
    );
}