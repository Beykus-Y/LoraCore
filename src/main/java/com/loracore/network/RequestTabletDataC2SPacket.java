// Файл: src/main/java/com/loracore/network/RequestTabletDataC2SPacket.java
package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec; // Импортируем PacketCodec
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestTabletDataC2SPacket() implements CustomPayload {
    public static final CustomPayload.Id<RequestTabletDataC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "request_tablet_data"));

    // ----> ИЗМЕНЕНИЕ: Используем PacketCodec.unit() для пакетов без полей
    public static final PacketCodec<RegistryByteBuf, RequestTabletDataC2SPacket> CODEC = PacketCodec.unit(new RequestTabletDataC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}