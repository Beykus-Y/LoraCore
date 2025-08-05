package com.loracore.component.data;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.Map;
import java.util.UUID;

/**
 * Хранит карту файловых систем для устройства. Ключ - слот (в виде строки), значение - UUID.
 * Этот компонент прикрепляется к главному предмету - планшету.
 */
// [ИЗМЕНЕНО] Тип ключа теперь String
public record FileSystemsData(Map<String, UUID> uuids) {

    // [ИЗМЕНЕНО] Codec.INT заменен на Codec.STRING
    public static final Codec<FileSystemsData> CODEC = Codec.unboundedMap(Codec.STRING, Uuids.CODEC)
            .xmap(FileSystemsData::new, FileSystemsData::uuids);

    // [ИЗМЕНЕНО] PacketCodecs.VAR_INT заменен на PacketCodecs.STRING
    public static final PacketCodec<RegistryByteBuf, FileSystemsData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, Uuids.PACKET_CODEC),
            FileSystemsData::uuids,
            FileSystemsData::new
    );
}