// Полный файл: src/main/java/com/loracore/network/graphics/GpuCommandC2SPacket.java
package com.loracore.network.graphics;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Унифицированный пакет-контейнер для отправки любой GpuCommand с клиента на сервер.
 * @param tabletUuid UUID планшета, к которому применяется команда.
 * @param command    Сама команда (Fill, DrawText и т.д.).
 */
public record GpuCommandC2SPacket(UUID tabletUuid, GpuCommand command) implements CustomPayload {

    public static final CustomPayload.Id<GpuCommandC2SPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "gpu_command"));

    public static final PacketCodec<RegistryByteBuf, GpuCommandC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, GpuCommandC2SPacket::tabletUuid,
            GpuCommand.CODEC, GpuCommandC2SPacket::command, // Используем наш кодек-диспетчер
            GpuCommandC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}