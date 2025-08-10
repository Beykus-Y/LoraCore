package com.loracore.network;

import com.loracore.LoraCoreMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Пакет, отправляемый с сервера клиенту, чтобы приказать ему
 * переключиться в режим клиентского Java-ядра.
 */
public record SwitchToClientKernelS2CPacket(String kernelPath) implements CustomPayload {

    public static final CustomPayload.Id<SwitchToClientKernelS2CPacket> ID =
            new CustomPayload.Id<>(new Identifier(LoraCoreMod.MOD_ID, "switch_to_kernel_s2c"));

    public static final PacketCodec<RegistryByteBuf, SwitchToClientKernelS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SwitchToClientKernelS2CPacket::kernelPath,
            SwitchToClientKernelS2CPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}