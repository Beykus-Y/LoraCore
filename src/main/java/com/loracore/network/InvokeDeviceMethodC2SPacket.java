// Новый файл: src/main/java/com/loracore/network/InvokeDeviceMethodC2SPacket.java
package com.loracore.network;

import com.google.gson.Gson;
import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import java.util.UUID;

public record InvokeDeviceMethodC2SPacket(
        UUID tabletUuid,
        int requestId,
        String deviceType,
        String methodName,
        String argsJson
) implements CustomPayload {
    public static final CustomPayload.Id<InvokeDeviceMethodC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "invoke_device"));
    private static final Gson GSON = new Gson();

    public static final PacketCodec<RegistryByteBuf, InvokeDeviceMethodC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, InvokeDeviceMethodC2SPacket::tabletUuid,
            PacketCodecs.VAR_INT, InvokeDeviceMethodC2SPacket::requestId,
            PacketCodecs.STRING, InvokeDeviceMethodC2SPacket::deviceType,
            PacketCodecs.STRING, InvokeDeviceMethodC2SPacket::methodName,
            PacketCodecs.STRING, InvokeDeviceMethodC2SPacket::argsJson,
            InvokeDeviceMethodC2SPacket::new
    );

    public Object[] getArgs() {
        return GSON.fromJson(argsJson, Object[].class);
    }

    public static String argsToJson(Object... args) {
        return GSON.toJson(args);
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}