// Файл: src/main/java/com/loracore/network/vfs/VfsResponseS2CPacket.java
package com.loracore.network.vfs;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record VfsResponseS2CPacket(
        UUID fsUuid,
        int callbackId,
        ResponseType type,
        String data,
        int chunkIndex,
        int totalChunks
) implements CustomPayload {
    public enum ResponseType { TRUE, FALSE, NIL, STRING, TABLE_JSON, LARGE_DATA }

    public static final CustomPayload.Id<VfsResponseS2CPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "vfs_response"));
    public static final PacketCodec<RegistryByteBuf, VfsResponseS2CPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, VfsResponseS2CPacket::fsUuid,
            PacketCodecs.VAR_INT, VfsResponseS2CPacket::callbackId,
            PacketCodecs.STRING.xmap(ResponseType::valueOf, ResponseType::name), VfsResponseS2CPacket::type,
            PacketCodecs.STRING, VfsResponseS2CPacket::data,
            PacketCodecs.VAR_INT, VfsResponseS2CPacket::chunkIndex,
            PacketCodecs.VAR_INT, VfsResponseS2CPacket::totalChunks,
            VfsResponseS2CPacket::new
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
    
    // Конструктор для обратной совместимости
    public VfsResponseS2CPacket(UUID fsUuid, int callbackId, ResponseType type, String data) {
        this(fsUuid, callbackId, type, data, 0, 1);
    }
}