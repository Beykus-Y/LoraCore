// Расположение: src/main/java/com/loracore/network/BootTabletS2CPacket.java
package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

// Пакет был значительно упрощен. Убраны bootScriptPath и architecture.
public record BootTabletS2CPacket(
        UUID fileSystemUuid,
        UUID tabletUuid,
        int totalRamKb
) implements CustomPayload {
    public static final CustomPayload.Id<BootTabletS2CPacket> ID = new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "boot_tablet"));

    public static final PacketCodec<RegistryByteBuf, BootTabletS2CPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, BootTabletS2CPacket::fileSystemUuid,
            Uuids.PACKET_CODEC, BootTabletS2CPacket::tabletUuid,
            PacketCodecs.VAR_INT, BootTabletS2CPacket::totalRamKb,
            BootTabletS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}