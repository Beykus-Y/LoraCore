// src/main/java/com/loracore/component/data/FirmwareData.java
package com.loracore.component.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.Identifier;

// Хранит путь к скрипту восстановления/прошивки
public record FirmwareData(Identifier recoveryScript) {
    public static final Codec<FirmwareData> CODEC = Identifier.CODEC.xmap(FirmwareData::new, FirmwareData::recoveryScript);
}