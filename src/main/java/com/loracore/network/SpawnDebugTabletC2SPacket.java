package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Пакет для запроса создания отладочного планшета "Flagship".
 */
public record SpawnDebugTabletC2SPacket() implements CustomPayload {
    public static final CustomPayload.Id<SpawnDebugTabletC2SPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "spawn_debug_tablet_c2s"));

    public static final PacketCodec<RegistryByteBuf, SpawnDebugTabletC2SPacket> CODEC = PacketCodec.unit(new SpawnDebugTabletC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
