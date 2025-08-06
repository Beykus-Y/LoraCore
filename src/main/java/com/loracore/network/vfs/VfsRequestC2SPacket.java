// Файл: src/main/java/com/loracore/network/vfs/VfsRequestC2SPacket.java
package com.loracore.network.vfs;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record VfsRequestC2SPacket(
        // +++ НОВОЕ ПОЛЕ +++
        int callbackId,
        UUID fsUuid,
        Operation operation,
        String path,
        String content
) implements CustomPayload {
    public enum Operation { EXISTS, READ, WRITE, MAKEDIR, ISDIR, LIST, DELETE }

    public static final CustomPayload.Id<VfsRequestC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "vfs_request"));
    public static final PacketCodec<RegistryByteBuf, VfsRequestC2SPacket> CODEC = PacketCodec.tuple(
            // +++ НОВЫЙ КОДЕК ДЛЯ ID +++
            PacketCodecs.VAR_INT, VfsRequestC2SPacket::callbackId,
            Uuids.PACKET_CODEC, VfsRequestC2SPacket::fsUuid,
            PacketCodecs.STRING.xmap(Operation::valueOf, Operation::name), VfsRequestC2SPacket::operation,
            PacketCodecs.STRING, VfsRequestC2SPacket::path,
            PacketCodecs.STRING, VfsRequestC2SPacket::content,
            VfsRequestC2SPacket::new
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}