package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AskAiC2SPacket(String question, String languageCode) implements CustomPayload {
    public static final CustomPayload.Id<AskAiC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "ask_ai"));
    public static final PacketCodec<RegistryByteBuf, AskAiC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AskAiC2SPacket::question,
            PacketCodecs.STRING, AskAiC2SPacket::languageCode,
            AskAiC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}