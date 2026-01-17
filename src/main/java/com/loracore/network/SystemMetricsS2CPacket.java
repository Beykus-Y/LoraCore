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
 * Пакет, отправляемый с сервера на клиент, содержащий метрики системы виртуальной машины.
 */
public record SystemMetricsS2CPacket(
        UUID tabletUuid,
        double cpuLoad,
        double ramUsedKb,
        double ramTotalKb,
        int diskQueue,
        String tabletUuidStr,
        String fsUuidStr
) implements CustomPayload {

    public static final CustomPayload.Id<SystemMetricsS2CPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "system_metrics_s2c"));

    public static final PacketCodec<RegistryByteBuf, SystemMetricsS2CPacket> CODEC = PacketCodec.ofStatic(
            (buf, value) -> {
                buf.writeUuid(value.tabletUuid());
                buf.writeDouble(value.cpuLoad());
                buf.writeDouble(value.ramUsedKb());
                buf.writeDouble(value.ramTotalKb());
                buf.writeVarInt(value.diskQueue());
                buf.writeString(value.tabletUuidStr());
                buf.writeString(value.fsUuidStr());
            },
            buf -> new SystemMetricsS2CPacket(
                    buf.readUuid(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readString()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
