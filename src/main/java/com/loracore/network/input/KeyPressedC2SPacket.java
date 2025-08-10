// Новый файл
package com.loracore.network.input;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Пакет для отправки события нажатия клавиши.
 */
public record KeyPressedC2SPacket(UUID tabletUuid, int keyCode, int scanCode, int modifiers) implements CustomPayload {

    public static final CustomPayload.Id<KeyPressedC2SPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "key_pressed_c2s"));

    public static final PacketCodec<RegistryByteBuf, KeyPressedC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, KeyPressedC2SPacket::tabletUuid,
            PacketCodecs.VAR_INT, KeyPressedC2SPacket::keyCode,
            PacketCodecs.VAR_INT, KeyPressedC2SPacket::scanCode,
            PacketCodecs.VAR_INT, KeyPressedC2SPacket::modifiers,
            KeyPressedC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}