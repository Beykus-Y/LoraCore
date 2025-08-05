// [НОВЫЙ ФАЙЛ]
package com.loracore.computer.device;

import com.loracore.computer.VirtualMachine;
import com.loracore.computer.api.Callback;

public class RamDevice {
    private final VirtualMachine vm;
    private final int totalSizeKb;

    public RamDevice(VirtualMachine vm, int totalSizeKb) {
        this.vm = vm;
        this.totalSizeKb = totalSizeKb;
    }

    @Callback(value = "getTotalSize", doc = "Returns the total amount of RAM in kilobytes.")
    public int getTotalSize() {
        return this.totalSizeKb;
    }

    @Callback(value = "getUsedSize", doc = "Returns the current memory usage in kilobytes.")
    public double getUsedSize() {
        return vm.getMemoryUsage();
    }
}