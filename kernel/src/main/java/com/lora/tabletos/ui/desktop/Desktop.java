package com.lora.tabletos.ui.desktop;

import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.IKernelVfs;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.apps.AboutApp;
import com.lora.tabletos.apps.SettingsApp;
import com.lora.tabletos.apps.SystemMonitorApp;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.ui.window.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Desktop {

    private static final Logger LOGGER = LoggerFactory.getLogger(Desktop.class);

    private final IKernelApi api;
    private final WindowManager windowManager;
    private final IKernelVfs vfs;
    private final List<DesktopIcon> icons = new ArrayList<>();

    /**
     * Record для хранения информации о встроенном приложении.
     */
    public record BuiltInApp(Class<? extends IApplication> appClass, String name, String iconLabel) {}
    
    /**
     * Список встроенных приложений, которые отображаются первыми на рабочем столе.
     */
    private static final List<BuiltInApp> BUILT_IN_APPS = List.of(
            new BuiltInApp(SystemMonitorApp.class, "System Monitor", "System Monitor"),
            new BuiltInApp(AboutApp.class, "About", "About"),
            new BuiltInApp(SettingsApp.class, "Settings", "Settings")
    );

    // Константы для расположения иконок
    private static final int ICON_START_X = 20;
    private static final int ICON_START_Y = 20;
    private static final int ICON_SPACING = 20;
    private static final int BACKGROUND_COLOR = 0xFF2D3E50;

    public Desktop(IKernelApi api, WindowManager windowManager) {
        this.api = api;
        this.windowManager = windowManager;
        this.vfs = api.getVfs();
    }

    public CompletableFuture<Boolean> initialize() {
        LOGGER.info("Desktop initialization started: loading icons...");
        String appsPath = "/home/user/apps";

        // Сначала добавляем встроенные приложения
        icons.clear();
        for (BuiltInApp builtInApp : BUILT_IN_APPS) {
            icons.add(new DesktopIcon(builtInApp.appClass, builtInApp.name, builtInApp.iconLabel));
        }

        // Затем загружаем внешние приложения из /home/user/apps
        return vfs.list(appsPath).thenApply(fileList -> {
            if (fileList != null) {
                for (String fileName : fileList) {
                    if (fileName.endsWith(".lua") || fileName.endsWith(".jar")) {
                        icons.add(new DesktopIcon(fileName, appsPath));
                    }
                }
            } else {
                LOGGER.warn("App directory '{}' does not exist or is empty.", appsPath);
            }
            layoutIcons();
            return true;

        }).exceptionally(throwable -> {
            LOGGER.error("Desktop initialization failed.", throwable);
            // Даже при ошибке показываем встроенные приложения
            layoutIcons();
            return true;
        });
    }

    private void layoutIcons() {
        int currentX = ICON_START_X;
        int currentY = ICON_START_Y;
        // ИСПРАВЛЕНИЕ: Используем правильное разрешение
        int screenWidth = 960;

        for (DesktopIcon icon : icons) {
            icon.setPosition(currentX, currentY);
            currentX += icon.getWidth() + ICON_SPACING;

            if (currentX + icon.getWidth() > screenWidth) {
                currentX = ICON_START_X;
                currentY += icon.getHeight() + ICON_SPACING + 10;
            }
        }
    }

    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, g.getWidth(), g.getHeight(), BACKGROUND_COLOR);
        for (DesktopIcon icon : icons) {
            icon.render(g);
        }
    }

    public boolean handleEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            for (DesktopIcon icon : icons) {
                if (icon.isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
                    LOGGER.info("Icon '{}' clicked.", icon.getLabel());
                    // Проверяем, является ли иконка встроенным приложением
                    if (icon.isBuiltIn()) {
                        windowManager.launchApp(icon.getAppClass());
                    } else {
                        windowManager.launchApp(icon.getScriptPath());
                    }
                    return true;
                }
            }
        }
        return false;
    }
}