package com.aiassist.network;

import com.aiassist.AiMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids; // ИЗМЕНЕНИЕ: Импорт для UUID кодека

import java.util.UUID;

public record SendDialogueMessageC2SPacket(UUID villagerUuid, String message, String languageCode) implements CustomPayload {
    public static final CustomPayload.Id<SendDialogueMessageC2SPacket> ID = new CustomPayload.Id<>(new Identifier(AiMod.MOD_ID, "send_dialogue_message"));

    public static final PacketCodec<RegistryByteBuf, SendDialogueMessageC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SendDialogueMessageC2SPacket::villagerUuid,
            PacketCodecs.STRING, SendDialogueMessageC2SPacket::message,
            PacketCodecs.STRING, SendDialogueMessageC2SPacket::languageCode, // Добавляем кодек для новой строки
            SendDialogueMessageC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}