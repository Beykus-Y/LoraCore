// Файл: src/main/java/com/loracore/network/vfs/VfsResponseS2CPacket.java
package com.loracore.network.vfs;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VfsResponseS2CPacket(
        // +++ НОВОЕ ПОЛЕ +++
        int callbackId,
        ResponseType type,
        String data
) implements CustomPayload {
    public enum ResponseType { TRUE, FALSE, NIL, STRING, TABLE_JSON }

    public static final CustomPayload.Id<VfsResponseS2CPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "vfs_response"));
    public static final PacketCodec<RegistryByteBuf, VfsResponseS2CPacket> CODEC = PacketCodec.tuple(
            // +++ НОВЫЙ КОДЕК ДЛЯ ID +++
            PacketCodecs.VAR_INT, VfsResponseS2CPacket::callbackId,
            PacketCodecs.STRING.xmap(ResponseType::valueOf, ResponseType::name), VfsResponseS2CPacket::type,
            PacketCodecs.STRING, VfsResponseS2CPacket::data,
            VfsResponseS2CPacket::new
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}