package com.loracore.computer.device;

import com.loracore.computer.IMemoryMappedDevice;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Read-only, survival-safe view of the world for native LoraOS programs.
 * The bridge exposes only the owning player and the block reachable by a
 * normal five-block raycast; writes are deliberately ignored.
 */
public final class WorldBridgeDevice implements IMemoryMappedDevice {
    public static final int SIZE = 64;
    public static final int ABI_VERSION = 1;
    public static final int CAP_PLAYER_POSITION = 1;
    public static final int CAP_TARGET_BLOCK = 2;
    public static final int CAP_REDSTONE_INPUT = 4;

    private final ServerPlayerEntity player;

    public WorldBridgeDevice(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public int getSize() {
        return SIZE;
    }

    @Override
    public byte read(int offset) {
        int value = readInt(offset & ~3);
        return (byte) (value >>> ((offset & 3) * 8));
    }

    @Override
    public int readInt(int offset) {
        BlockPos playerPos = player.getBlockPos();
        TargetSnapshot target = targetSnapshot();
        return switch (offset) {
            case 0x00 -> ABI_VERSION;
            case 0x04 -> CAP_PLAYER_POSITION | CAP_TARGET_BLOCK | CAP_REDSTONE_INPUT;
            case 0x08 -> playerPos.getX();
            case 0x0C -> playerPos.getY();
            case 0x10 -> playerPos.getZ();
            case 0x14 -> player.getHorizontalFacing().getId();
            case 0x18 -> target.available ? 1 : 0;
            case 0x1C -> target.blockId;
            case 0x20 -> target.redstonePower;
            case 0x24 -> target.x;
            case 0x28 -> target.y;
            case 0x2C -> target.z;
            default -> 0;
        };
    }

    @Override
    public void write(int offset, byte value) {
        // ABI v1 is intentionally read-only.
    }

    @Override
    public void writeInt(int offset, int value) {
        // ABI v1 is intentionally read-only.
    }

    private TargetSnapshot targetSnapshot() {
        HitResult hit = player.raycast(5.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return TargetSnapshot.EMPTY;
        }
        BlockPos pos = blockHit.getBlockPos();
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        return new TargetSnapshot(true, Registries.BLOCK.getRawId(state.getBlock()),
                world.getReceivedRedstonePower(pos), pos.getX(), pos.getY(), pos.getZ());
    }

    private record TargetSnapshot(boolean available, int blockId, int redstonePower,
                                  int x, int y, int z) {
        private static final TargetSnapshot EMPTY = new TargetSnapshot(false, 0, 0, 0, 0, 0);
    }
}
