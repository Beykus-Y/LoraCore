package com.loracore.network;

import com.loracore.AiMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record CompleteQuestC2SPacket(UUID villagerUuid) implements CustomPayload {
    public static final CustomPayload.Id<CompleteQuestC2SPacket> ID = new CustomPayload.Id<>(new Identifier(AiMod.MOD_ID, "complete_quest"));
    public static final PacketCodec<RegistryByteBuf, CompleteQuestC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, CompleteQuestC2SPacket::villagerUuid,
            CompleteQuestC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}