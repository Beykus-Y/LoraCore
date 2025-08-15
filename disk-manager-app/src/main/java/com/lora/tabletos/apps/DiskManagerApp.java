// --- Новый файл: disk-manager-app/src/main/java/com/lora/tabletos/apps/DiskManagerApp.java ---
package com.lora.tabletos.apps;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lora.tabletos.core.DeviceManager;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiskManagerApp implements IApplication {

    private IApplicationApi api;
    private DeviceManager deviceManager;
    private String statusText = "Loading disk information...";
    private final List<DiskInfo> disks = new ArrayList<>();
    private int selectedDisk = -1;
    private boolean isLoading = false;

    // UI-константы
    private static final int BG_COLOR = 0xFF2c3e50;
    private static final int LIST_BG_COLOR = 0xFF1e2b38;
    private static final int TEXT_COLOR = 0xFFecf0f1;
    private static final int HIGHLIGHT_COLOR = 0xFF3498db;
    private static final int BUTTON_COLOR = 0xFFc0392b;
    private static final int BUTTON_HOVER_COLOR = 0xFFe74c3c;

    // Внутренний класс для хранения информации о диске
    private static class DiskInfo {
        final String name;
        final String uuid;
        Map<String, Object> details;

        DiskInfo(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        this.deviceManager = new DeviceManager(api);
        refreshDisks();
    }

    private void refreshDisks() {
        disks.clear();
        selectedDisk = -1;
        statusText = "Scanning for disks...";
        isLoading = true;

        deviceManager.listDisks().whenComplete((diskList, error) -> {
            api.runOnRenderThread(() -> {
                isLoading = false;
                if (error != null) {
                    statusText = "Error scanning: " + error.getMessage();
                } else if (diskList.isEmpty()) {
                    statusText = "No storage devices found.";
                } else {
                    for (Map<String, String> diskData : diskList) {
                        disks.add(new DiskInfo(diskData.get("name"), diskData.get("uuid")));
                    }
                    statusText = "Scan complete. Select a disk.";
                }
            });
        });
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, 480, 240, BG_COLOR);
        g.drawString("Disk Manager", 10, 8, TEXT_COLOR);

        int listWidth = 150;
        g.fill(10, 25, 10 + listWidth, 230, LIST_BG_COLOR);

        int y = 30;
        for (int i = 0; i < disks.size(); i++) {
            DiskInfo disk = disks.get(i);
            String diskLabel = disk.name + " (" + disk.uuid.substring(0, 8) + ")";
            if (i == selectedDisk) {
                g.fill(12, y - 2, 8 + listWidth, y + 10, HIGHLIGHT_COLOR);
            }
            g.drawString(diskLabel, 15, y, TEXT_COLOR);
            y += 15;
        }

        if (selectedDisk != -1 && selectedDisk < disks.size()) {
            DiskInfo disk = disks.get(selectedDisk);
            g.drawString("UUID: " + disk.uuid, listWidth + 20, 30, TEXT_COLOR);
            if (disk.details != null) {
                g.drawString("Size: " + disk.details.getOrDefault("size", "N/A") + " bytes", listWidth + 20, 45, TEXT_COLOR);
            }

            int buttonX = listWidth + 20;
            int buttonY = 100;
            int buttonW = 100;
            int buttonH = 30;

            boolean isHovered = mouseX > buttonX && mouseX < buttonX + buttonW && mouseY > buttonY && mouseY < buttonY + buttonH;
            g.fill(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH, isHovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
            g.drawCenteredString("Format", buttonX + buttonW / 2, buttonY + 11, TEXT_COLOR);
        }

        g.drawString(statusText, 10, 230, TEXT_COLOR);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        if (isLoading) return false;

        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            int y = 30;
            for (int i = 0; i < disks.size(); i++) {
                if (mouseEvent.mouseX > 10 && mouseEvent.mouseX < 160 && mouseEvent.mouseY > y - 2 && mouseEvent.mouseY < y + 10) {
                    selectedDisk = i;
                    statusText = "Selected: " + disks.get(i).name;
                    loadDiskDetails(i);
                    return true;
                }
                y += 15;
            }

            if (selectedDisk != -1 && mouseEvent.mouseX > 170 && mouseEvent.mouseX < 270 && mouseEvent.mouseY > 100 && mouseEvent.mouseY < 130) {
                formatSelectedDisk();
                return true;
            }
        }
        return false;
    }

    private void loadDiskDetails(int diskIndex) {
        if (diskIndex >= 0 && diskIndex < disks.size()) {
            DiskInfo disk = disks.get(diskIndex);
            statusText = "Fetching info for " + disk.name + "...";
            isLoading = true;

            deviceManager.getDiskInfo(disk.uuid).whenComplete((info, error) -> {
                api.runOnRenderThread(() -> {
                    isLoading = false;
                    if (error != null) {
                        statusText = "Error getting details: " + error.getMessage();
                    } else {
                        disk.details = info;
                        statusText = "Details loaded for " + disk.name;
                    }
                });
            });
        }
    }

    private void formatSelectedDisk() {
        if (selectedDisk != -1 && selectedDisk < disks.size()) {
            String uuidToFormat = disks.get(selectedDisk).uuid;
            statusText = "Formatting " + uuidToFormat.substring(0, 8) + "...";
            isLoading = true;

            deviceManager.formatDisk(uuidToFormat).whenComplete((success, error) -> {
                api.runOnRenderThread(() -> {
                    isLoading = false;
                    if (error != null) {
                        statusText = "Format failed: " + error.getMessage();
                    } else if (success) {
                        statusText = "Format successful! Refreshing...";
                        refreshDisks();
                    } else {
                        statusText = "Format failed on server.";
                    }
                });
            });
        }
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
    @Override public void onClose() {}
}