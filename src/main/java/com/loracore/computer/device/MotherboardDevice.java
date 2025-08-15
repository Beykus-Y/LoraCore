// --- Новый файл: src/main/java/com/loracore/computer/device/MotherboardDevice.java ---
package com.loracore.computer.device;

import com.google.gson.Gson;
import com.loracore.component.ModComponents;
import com.loracore.component.data.FileSystemsData;
import com.loracore.component.data.MotherboardData;
import com.loracore.computer.VirtualMachineManager;
import com.loracore.computer.api.Callback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MotherboardDevice implements IDevice {
    private PlayerEntity player;
    private static final Gson GSON = new Gson();

    @Override
    public String getType() {
        return "motherboard";
    }

    @Override
    public void rebind(PlayerEntity player, ServerWorld world, BlockPos targetPos) {
        this.player = player;
    }

    @Override
    public boolean isAvailable() {
        return player != null;
    }

    @Callback(doc = "Returns a list of installed storage devices with their UUIDs.")
    public String getStorageDevices() {
        ItemStack tabletStack = player.getMainHandStack(); // Предполагаем, что планшет в руке
        MotherboardData mobo = tabletStack.get(ModComponents.MOTHERBOARD_DATA);
        if (mobo == null) return "[]";

        List<Map<String, String>> result = new ArrayList<>();
        for (ItemStack hddStack : mobo.storage()) {
            FileSystemsData fsData = hddStack.get(ModComponents.FILE_SYSTEMS_DATA);
            if (fsData != null) {
                result.add(Map.of(
                        "name", hddStack.getName().getString(),
                        "uuid", fsData.fsUuid().toString()
                ));
            }
        }
        return GSON.toJson(result);
    }
}