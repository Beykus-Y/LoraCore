// Полный исправленный файл: src/main/java/com/loracore/network/graphics/GpuCommand.java
package com.loracore.network.graphics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.function.ValueLists;

import java.util.function.IntFunction;

/**
 * Запечатанный интерфейс, представляющий все возможные графические команды.
 * (Версия 12.0 - Финальная. Используется правильная двухэтапная диспетчеризация).
 */
public sealed interface GpuCommand {

    // --- ГЛАВНЫЙ КОДЕК-ДИСПЕТЧЕР ---
    // Ссылка на финальный кодек, который будет определен во вложенном enum.
    PacketCodec<RegistryByteBuf, GpuCommand> CODEC = GpuCommandType.DISPATCH_CODEC;

    GpuCommandType getType();

    // --- КОНКРЕТНЫЕ РЕАЛИЗАЦИИ КОМАНД ---

    record Fill(int x, int y, int width, int height, int color) implements GpuCommand {
        public static final PacketCodec<RegistryByteBuf, Fill> INNER_CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Fill::x, PacketCodecs.VAR_INT, Fill::y,
                PacketCodecs.VAR_INT, Fill::width, PacketCodecs.VAR_INT, Fill::height,
                PacketCodecs.VAR_INT, Fill::color, Fill::new
        );
        @Override public GpuCommandType getType() { return GpuCommandType.FILL; }
    }

    record DrawText(int x, int y, String text, int color) implements GpuCommand {
        public static final PacketCodec<RegistryByteBuf, DrawText> INNER_CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, DrawText::x, PacketCodecs.VAR_INT, DrawText::y,
                PacketCodecs.STRING, DrawText::text, PacketCodecs.VAR_INT, DrawText::color,
                DrawText::new
        );
        @Override public GpuCommandType getType() { return GpuCommandType.DRAW_TEXT; }
    }

    record Copy(int x, int y, int width, int height, int toX, int toY) implements GpuCommand {
        public static final PacketCodec<RegistryByteBuf, Copy> INNER_CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Copy::x, PacketCodecs.VAR_INT, Copy::y,
                PacketCodecs.VAR_INT, Copy::width, PacketCodecs.VAR_INT, Copy::height,
                PacketCodecs.VAR_INT, Copy::toX, PacketCodecs.VAR_INT, Copy::toY,
                Copy::new
        );
        @Override public GpuCommandType getType() { return GpuCommandType.COPY; }
    }

    /**
     * Enum, который служит диспетчером для наших команд.
     */
    enum GpuCommandType {
        FILL(Fill.INNER_CODEC),
        DRAW_TEXT(DrawText.INNER_CODEC),
        COPY(Copy.INNER_CODEC);

        // --- Логика диспетчера ---
        private static final IntFunction<GpuCommandType> BY_ID = ValueLists.createIdToValueFunction(
                GpuCommandType::ordinal, values(), ValueLists.OutOfBoundsHandling.ZERO
        );

        // Шаг 1: Создаем кодек, который умеет работать ТОЛЬКО с этим enum, вручную.
        // Исправленная версия TYPE_CODEC с явным указанием типов
        private static final PacketCodec<RegistryByteBuf, GpuCommandType> TYPE_CODEC =
                PacketCodec.ofStatic(
                        GpuCommandType::encode,  // method reference на статический метод
                        GpuCommandType::decode   // method reference на статический метод
                );

        private static void encode(RegistryByteBuf buffer, GpuCommandType commandType) {
            buffer.writeVarInt(commandType.ordinal());
        }

        private static GpuCommandType decode(RegistryByteBuf buffer) {
            return BY_ID.apply(buffer.readVarInt());
        }

        // Шаг 2: Используем этот надежный кодек для диспетчеризации.
        public static final PacketCodec<RegistryByteBuf, GpuCommand> DISPATCH_CODEC =
                TYPE_CODEC.dispatch(GpuCommand::getType, GpuCommandType::getCodec);

        private final PacketCodec<RegistryByteBuf, ? extends GpuCommand> codec;

        GpuCommandType(PacketCodec<RegistryByteBuf, ? extends GpuCommand> codec) {
            this.codec = codec;
        }

        PacketCodec<RegistryByteBuf, ? extends GpuCommand> getCodec() {
            return this.codec;
        }
    }
}