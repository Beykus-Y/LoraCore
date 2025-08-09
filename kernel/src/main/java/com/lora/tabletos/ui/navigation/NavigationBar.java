// Файл: kernel/src/main/java/com/lora/tabletos/ui/navigation/NavigationBar.java
package com.lora.tabletos.ui.navigation;

import com.lora.tabletos.ui.window.WindowManager;
import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Интерактивная панель навигации (Dock), отображающая запущенные приложения
 * и позволяющая переключаться между ними или вернуться на рабочий стол.
 */
public class NavigationBar {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavigationBar.class);
    private final IKernelApi api;
    private WindowManager windowManager;

    // --- UI Константы ---
    private static final int BAR_HEIGHT = 30;
    private static final int BAR_COLOR = 0xEE2C3A47; // Слегка прозрачный темно-серый
    private static final int ICON_SIZE = 24;
    private static final int ICON_PADDING = (BAR_HEIGHT - ICON_SIZE) / 2; // = 3
    private static final int ICON_SPACING = 5;
    private static final int HOME_BUTTON_RADIUS = 13;
    private static final int ICON_BG_COLOR = 0x50FFFFFF; // Полупрозрачный белый
    private static final int ICON_BG_ACTIVE_COLOR = 0xFF4CAF50; // Ярко-зеленый для активного приложения
    private static final int ICON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOME_ICON_COLOR = 0xFFDDDDDD;

    // --- Внутреннее состояние ---
    private final List<DockIcon> dockIcons = new ArrayList<>();
    private ClickableArea homeButtonArea;

    // Вспомогательные рекорды для управления кликабельными зонами
    private record ClickableArea(int x, int y, int width, int height) {
        boolean isClicked(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
    private record DockIcon(UUID appId, String label, ClickableArea area) {}

    public NavigationBar(IKernelApi api) {
        this.api = api;
    }

    public void setWindowManager(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    /**
     * Отрисовывает фон панели, кнопку "Домой" и иконки запущенных приложений.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        if (windowManager == null) return;

        int screenHeight = g.getHeight();
        int screenWidth = g.getWidth();
        int barY = screenHeight - BAR_HEIGHT;

        // 1. Рисуем фон панели
        g.fill(0, barY, screenWidth, screenHeight, BAR_COLOR);

        // 2. Обновляем список иконок и их расположение
        updateAndLayoutIcons(screenWidth, barY);

        // 3. Рисуем кнопку "Домой"
        g.fill(homeButtonArea.x(), homeButtonArea.y(), homeButtonArea.x() + homeButtonArea.width(), homeButtonArea.y() + homeButtonArea.height(), ICON_BG_COLOR);
        drawHomeIcon(g, homeButtonArea.x(), homeButtonArea.y());

        // 4. Рисуем иконки запущенных приложений
        UUID activeAppId = windowManager.getActiveAppId();
        for (DockIcon icon : dockIcons) {
            boolean isActive = icon.appId().equals(activeAppId);
            int bgColor = isActive ? ICON_BG_ACTIVE_COLOR : ICON_BG_COLOR;

            // Фон иконки
            g.fill(icon.area().x, icon.area().y, icon.area().x + icon.area().width, icon.area().y + icon.area().height, bgColor);

            // Первая буква названия как иконка
            String iconText = icon.label().isEmpty() ? "?" : icon.label().substring(0, 1).toUpperCase();
            g.drawCenteredString(iconText, icon.area().x + (ICON_SIZE / 2), icon.area().y + 8, ICON_TEXT_COLOR);
        }
    }
    /**
     * Рисует иконку домика с помощью примитивов.
     */
    private void drawHomeIcon(IKernelGraphics g, int x, int y) {
        int roofY = y + 5;
        int houseY = y + 13;
        // Крыша (треугольник)
        g.fill(x + 12, roofY, x + 14, roofY + 2, HOME_ICON_COLOR);
        g.fill(x + 10, roofY + 2, x + 16, roofY + 4, HOME_ICON_COLOR);
        g.fill(x + 8, roofY + 4, x + 18, roofY + 6, HOME_ICON_COLOR);
        g.fill(x + 6, roofY + 6, x + 20, roofY + 8, HOME_ICON_COLOR);
        // Основание дома
        g.fill(x + 6, houseY, x + 20, y + 21, HOME_ICON_COLOR);
    }

    /**
     * Обрабатывает клики по кнопке "Домой" и иконкам приложений.
     */
    public boolean handleEvent(KernelEvent event) {
        if (windowManager == null || !(event instanceof KernelEvent.MouseClicked mouseEvent)) {
            return false;
        }

        // Проверяем клик по кнопке "Домой"
        if (homeButtonArea != null && homeButtonArea.isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
            LOGGER.info("Нажата кнопка 'Домой'. Возвращаемся на рабочий стол.");
            windowManager.switchToApp(null); // null означает "на рабочий стол"
            return true;
        }

        // Проверяем клик по одной из иконок приложений
        for (DockIcon icon : dockIcons) {
            if (icon.area().isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
                LOGGER.info("Клик по иконке '{}'. Переключаемся на приложение {}.", icon.label(), icon.appId());
                windowManager.switchToApp(icon.appId());
                return true;
            }
        }

        // Если клик был просто по панели, но не по кнопке, тоже считаем его обработанным
        int screenHeight = api.getGraphics().getHeight();
        if (mouseEvent.mouseY >= screenHeight - BAR_HEIGHT) {
            return true;
        }

        return false;
    }

    /**
     * Обновляет внутренний список иконок на основе запущенных приложений из WindowManager
     * и рассчитывает их координаты на экране.
     */
    private void updateAndLayoutIcons(int screenWidth, int barY) {
        // Очищаем старые иконки
        dockIcons.clear();

        // Располагаем кнопку "Домой" по центру
        int homeButtonSize = HOME_BUTTON_RADIUS * 2;
        int homeX = (screenWidth / 2) - HOME_BUTTON_RADIUS;
        int homeY = barY + (BAR_HEIGHT - homeButtonSize) / 2;
        homeButtonArea = new ClickableArea(homeX, homeY, homeButtonSize, homeButtonSize);

        // Располагаем иконки приложений слева от кнопки "Домой"
        int currentX = homeX - ICON_SPACING - ICON_SIZE;
        Map<UUID, WindowManager.AppInfo> runningApps = windowManager.getRunningApps();

        for (UUID appId : new ArrayList<>(runningApps.keySet())) {
            WindowManager.AppInfo appInfo = runningApps.get(appId);
            if (appInfo == null) continue;

            String label = appInfo.path().substring(appInfo.path().lastIndexOf('/') + 1)
                    .replace(".jar", "").replace(".lua", "");
            ClickableArea area = new ClickableArea(currentX, barY + ICON_PADDING, ICON_SIZE, ICON_SIZE);
            dockIcons.add(new DockIcon(appId, label, area));

            // Сдвигаем позицию для следующей иконки
            currentX -= (ICON_SIZE + ICON_SPACING);
        }
    }

    public void shutdown() {
        LOGGER.info("NavigationBar shutting down...");
    }
}