// Файл: src/main/java/com/loracore/component/data/FileSystemsData.java
package com.loracore.component.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Uuids;

import java.util.Map;
import java.util.UUID;

public record FileSystemsData(UUID fsUuid) {

    // --- НАЧАЛО МАГИИ МИГРАЦИИ ---

    // Кодек для старого формата: Map<String, UUID>
    private static final Codec<Map<String, UUID>> LEGACY_CODEC = Codec.unboundedMap(Codec.STRING, Uuids.CODEC);

    // Универсальный кодек, который может читать И старый, И новый формат
    public static final Codec<FileSystemsData> CODEC = Codec.either(Uuids.CODEC, LEGACY_CODEC).xmap(
            either -> either.map(
                    FileSystemsData::new, // Если прочитали UUID (новый формат), создаем объект
                    map -> new FileSystemsData(map.getOrDefault("0", UUID.randomUUID())) // Если прочитали Map (старый формат), извлекаем UUID
            ),
            // При записи ВСЕГДА используем новый формат (UUID)
            data -> Either.left(data.fsUuid())
    );

    // --- КОНЕЦ МАГИИ МИГРАЦИИ ---

    // Сетевой кодек остается простым, так как по сети всегда передается новый формат
    public static final PacketCodec<RegistryByteBuf, FileSystemsData> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public FileSystemsData decode(RegistryByteBuf buf) {
            return new FileSystemsData(buf.readUuid());
        }

        @Override
        public void encode(RegistryByteBuf buf, FileSystemsData value) {
            buf.writeUuid(value.fsUuid());
        }
    };
}