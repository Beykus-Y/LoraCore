
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
 * Пакет для отправки события ввода символа.
 */
public record CharTypedC2SPacket(UUID tabletUuid, char chr, int modifiers) implements CustomPayload {

    public static final CustomPayload.Id<CharTypedC2SPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "char_typed_c2s"));

    // Вспомогательный кодек для типа char
    private static final PacketCodec<RegistryByteBuf, Character> CHAR_CODEC = PacketCodec.ofStatic(
            (buf, c) -> buf.writeChar(c),
            RegistryByteBuf::readChar
    );

    public static final PacketCodec<RegistryByteBuf, CharTypedC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, CharTypedC2SPacket::tabletUuid,
            CHAR_CODEC, CharTypedC2SPacket::chr,
            PacketCodecs.VAR_INT, CharTypedC2SPacket::modifiers,
            CharTypedC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}