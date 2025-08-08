// Полный исправленный файл: src/main/java/com/loracore/network/graphics/ScreenUpdateS2CPacket.java
package com.loracore.network.graphics;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Пакет, отправляемый с сервера на клиент, содержащий полный
 * буфер пикселей экрана планшета для обновления.
 * (Версия 3.0 - Исправлена ошибка с кодеком массива байтов).
 */
public record ScreenUpdateS2CPacket(UUID tabletUuid, byte[] pixelBuffer) implements CustomPayload {

    public static final CustomPayload.Id<ScreenUpdateS2CPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "screen_update"));

    public static final PacketCodec<RegistryByteBuf, ScreenUpdateS2CPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, ScreenUpdateS2CPacket::tabletUuid,
            // ИСПРАВЛЕНИЕ: Заменяем проблемный 'limitedBytes' на стандартный и надежный 'BYTE_ARRAY'.
            // Ограничение размера теперь является ответственностью сервера, который создает этот пакет.
            PacketCodecs.BYTE_ARRAY, ScreenUpdateS2CPacket::pixelBuffer,
            ScreenUpdateS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}