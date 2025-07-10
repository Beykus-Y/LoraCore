package com.aiassist.network;

import com.aiassist.AiMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs; // Добавлен необходимый импорт
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

// ИЗМЕНЕНИЕ 1: Добавлено поле languageCode
public record RequestVillagerDataC2SPacket(UUID villagerUuid, String languageCode) implements CustomPayload {
    public static final CustomPayload.Id<RequestVillagerDataC2SPacket> ID = new CustomPayload.Id<>(new Identifier(AiMod.MOD_ID, "request_villager_data"));

    // ИЗМЕНЕНИЕ 2: Кодек заменен на tuple для обработки двух полей
    public static final PacketCodec<RegistryByteBuf, RequestVillagerDataC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, RequestVillagerDataC2SPacket::villagerUuid,
            PacketCodecs.STRING, RequestVillagerDataC2SPacket::languageCode,
            RequestVillagerDataC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}