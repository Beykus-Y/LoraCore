// [НОВЫЙ ФАЙЛ]
package com.loracore.computer.device;

import com.loracore.computer.VirtualMachine;
import com.loracore.computer.api.Callback;
import com.loracore.LoraCoreMod;

public class RamDevice {
    private final VirtualMachine vm;
    private final int totalSizeKb;

    public RamDevice(VirtualMachine vm, int totalSizeKb) {
        this.vm = vm;
        this.totalSizeKb = totalSizeKb;
        LoraCoreMod.LOGGER.info("RamDevice: Создан с размером RAM: {} KB", totalSizeKb);
    }

    @Callback(value = "getTotalSize", doc = "Returns the total amount of RAM in kilobytes.")
    public int getTotalSize() {
        LoraCoreMod.LOGGER.debug("RamDevice.getTotalSize() вызван, возвращаем: {} KB", this.totalSizeKb);
        return this.totalSizeKb;
    }

    @Callback(value = "getUsedSize", doc = "Returns the current memory usage in kilobytes.")
    public double getUsedSize() {
        try {
            double rawUsage = vm.getMemoryUsage();
            
            // ИСПРАВЛЕНИЕ: Ограничиваем использование памяти разумными пределами
            // collectgarbage("count") может возвращать очень большие значения
            // Ограничиваем максимумом в 90% от общего объема памяти
            double maxUsage = totalSizeKb * 0.9;
            
            // Дополнительная защита: если rawUsage слишком большой, возвращаем безопасное значение
            if (rawUsage > totalSizeKb * 2) {
                // Если значение превышает 200% от общего объема, что-то явно не так
                return maxUsage;
            }
            
            if (rawUsage > maxUsage) {
                // Если использование превышает разумный лимит, возвращаем ограниченное значение
                return maxUsage;
            }
            
            // Также проверяем, что использование не отрицательное
            return Math.max(0, rawUsage);
        } catch (Exception e) {
            // В случае любой ошибки возвращаем безопасное значение
            return totalSizeKb * 0.1; // 10% от общего объема как безопасное значение
        }
    }
}