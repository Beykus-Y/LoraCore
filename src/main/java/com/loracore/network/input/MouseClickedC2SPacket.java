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
 * Пакет для отправки события клика мыши.
 */
public record MouseClickedC2SPacket(UUID tabletUuid, double x, double y, int button) implements CustomPayload {

    public static final CustomPayload.Id<MouseClickedC2SPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "mouse_clicked_c2s"));

    public static final PacketCodec<RegistryByteBuf, MouseClickedC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, MouseClickedC2SPacket::tabletUuid,
            PacketCodecs.DOUBLE, MouseClickedC2SPacket::x,
            PacketCodecs.DOUBLE, MouseClickedC2SPacket::y,
            PacketCodecs.VAR_INT, MouseClickedC2SPacket::button,
            MouseClickedC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}