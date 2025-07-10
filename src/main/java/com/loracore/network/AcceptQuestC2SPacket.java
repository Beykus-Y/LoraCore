package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

// ИЗМЕНЕНИЕ: Добавлено поле languageCode
public record AcceptQuestC2SPacket(UUID villagerUuid, String languageCode) implements CustomPayload {
    public static final CustomPayload.Id<AcceptQuestC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "accept_quest"));

    // ИЗМЕНЕНИЕ: Обновлен кодек для включения languageCode
    public static final PacketCodec<RegistryByteBuf, AcceptQuestC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, AcceptQuestC2SPacket::villagerUuid,
            PacketCodecs.STRING, AcceptQuestC2SPacket::languageCode,
            AcceptQuestC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}