package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record GiveGiftC2SPacket(UUID villagerUuid) implements CustomPayload {
    public static final CustomPayload.Id<GiveGiftC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "give_gift"));
    public static final PacketCodec<RegistryByteBuf, GiveGiftC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, GiveGiftC2SPacket::villagerUuid,
            GiveGiftC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}