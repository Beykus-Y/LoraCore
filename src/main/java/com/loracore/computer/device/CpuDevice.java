package com.loracore.computer.device;

import com.loracore.computer.VirtualMachine;
import com.loracore.computer.api.Callback;

public class CpuDevice {
    private final VirtualMachine vm;
    private final String architecture;

    // [ИСПРАВЛЕНО] Конструктор теперь принимает и VM, и архитектуру.
    public CpuDevice(VirtualMachine vm, String architecture) {
        this.vm = vm;
        this.architecture = architecture;
    }

    @Callback(value = "getTime", doc = "Returns the uptime of the virtual machine in seconds.")
    public double getTime() {
        return vm.getUptime();
    }

    @Callback(value = "getArchitecture", doc = "Returns the CPU architecture name.")
    public String getArchitecture() {
        return this.architecture;
    }
}