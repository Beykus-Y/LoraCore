// Расположение: src/main/java/com/loracore/network/RunLuaScriptC2SPacket.java
package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * Пакет, отправляемый с клиента на сервер, когда Java-ядро
 * хочет запустить Lua-скрипт.
 */
public record RunLuaScriptC2SPacket(UUID tabletUuid, String scriptPath) implements CustomPayload {
    public static final CustomPayload.Id<RunLuaScriptC2SPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "run_lua_script"));
    public static final PacketCodec<RegistryByteBuf, RunLuaScriptC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, RunLuaScriptC2SPacket::tabletUuid,
            PacketCodecs.STRING, RunLuaScriptC2SPacket::scriptPath,
            RunLuaScriptC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}