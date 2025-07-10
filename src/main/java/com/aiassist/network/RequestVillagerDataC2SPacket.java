package com.aiassist.network;

import com.aiassist.AiMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids; // ИЗМЕНЕНИЕ: Правильный импорт для UUID кодека

import java.util.UUID;

public record RequestVillagerDataC2SPacket(UUID villagerUuid) implements CustomPayload {
    public static final CustomPayload.Id<RequestVillagerDataC2SPacket> ID = new CustomPayload.Id<>(new Identifier(AiMod.MOD_ID, "request_villager_data"));
    // ИЗМЕНЕНИЕ: Используем правильный кодек для UUID
    public static final PacketCodec<RegistryByteBuf, RequestVillagerDataC2SPacket> CODEC = PacketCodec.of(
            (value, buf) -> Uuids.PACKET_CODEC.encode(buf, value.villagerUuid),
            (buf) -> new RequestVillagerDataC2SPacket(Uuids.PACKET_CODEC.decode(buf))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}