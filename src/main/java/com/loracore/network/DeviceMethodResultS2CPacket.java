// Новый файл: src/main/java/com/loracore/network/DeviceMethodResultS2CPacket.java
package com.loracore.network;

import com.google.gson.Gson;
import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeviceMethodResultS2CPacket(
        int requestId,
        boolean success,
        String resultJson
) implements CustomPayload {
    public static final CustomPayload.Id<DeviceMethodResultS2CPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "device_result"));
    private static final Gson GSON = new Gson();

    public static final PacketCodec<RegistryByteBuf, DeviceMethodResultS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DeviceMethodResultS2CPacket::requestId,
            PacketCodecs.BOOL, DeviceMethodResultS2CPacket::success,
            PacketCodecs.STRING, DeviceMethodResultS2CPacket::resultJson,
            DeviceMethodResultS2CPacket::new
    );

    public Object[] getResult() {
        if (!success) return new Object[]{resultJson}; // В случае ошибки resultJson это текст ошибки
        return GSON.fromJson(resultJson, Object[].class);
    }

    public static String resultToJson(Object result) {
        return GSON.toJson(new Object[]{result});
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}