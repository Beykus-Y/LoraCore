package com.loracore.network;

import com.loracore.AiMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record SetVillagerFrozenC2SPacket(UUID villagerUuid, boolean frozen) implements CustomPayload {
    public static final CustomPayload.Id<SetVillagerFrozenC2SPacket> ID = new CustomPayload.Id<>(new Identifier(AiMod.MOD_ID, "set_villager_frozen"));

    public static final PacketCodec<RegistryByteBuf, SetVillagerFrozenC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SetVillagerFrozenC2SPacket::villagerUuid,
            PacketCodecs.BOOL, SetVillagerFrozenC2SPacket::frozen,
            SetVillagerFrozenC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}