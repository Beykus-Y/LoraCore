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
 * Стиль: macOS Dock (парящая панель с отступами).
 */
public class NavigationBar {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavigationBar.class);
    private final IKernelApi api;
    private WindowManager windowManager;

    // Новый компонент (Меню Пуск)
    private final SystemMenu systemMenu;

    // --- UI Константы (Modern Dock) ---
    private static final int DOCK_HEIGHT = 40;
    private static final int DOCK_BOTTOM_MARGIN = 10;
    private static final int ICON_SIZE = 32; // Иконки побольше
    private static final int ICON_SPACING = 8;

    // Цвета (Glassmorphism)
    private static final int DOCK_BG_COLOR = 0xCC202020; // Сильно прозрачный темный фон
    private static final int DOCK_BORDER_COLOR = 0x40FFFFFF; // Тонкая белая обводка
    private static final int ICON_TEXT_COLOR = 0xFFFFFFFF;
    private static final int HOME_ICON_COLOR = 0xFFDDDDDD;
    private static final int START_BTN_COLOR = 0xFFFF5722; // Оранжевый для кнопки пуск

    // --- Внутреннее состояние ---
    private final List<DockIcon> dockIcons = new ArrayList<>();
    private ClickableArea homeButtonArea;
    private ClickableArea startButtonArea; // Зона кнопки Пуск

    // Вспомогательные рекорды для управления кликабельными зонами
    private record ClickableArea(int x, int y, int width, int height) {
        boolean isClicked(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
    private record DockIcon(UUID appId, String label, ClickableArea area) {}

    public NavigationBar(IKernelApi api) {
        this.api = api;
        // Инициализируем меню
        this.systemMenu = new SystemMenu(api);
    }

    public void setWindowManager(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    /**
     * Отрисовывает фон панели, кнопку "Пуск", иконки приложений и кнопку "Домой".
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        if (windowManager == null) return;

        int screenW = g.getWidth();
        int screenH = g.getHeight();

        // Считаем ширину дока в зависимости от количества иконок
        // Пуск + разделитель + (Иконки приложений) + разделитель + Домой
        int appCount = windowManager.getRunningApps().size();
        int totalContentWidth = ICON_SIZE // Пуск
                + ICON_SPACING + 2 // Разделитель
                + (appCount * (ICON_SIZE + ICON_SPACING))
                + 2 // Разделитель (потенциальный)
                + ICON_SIZE; // Домой

        // Минимальная ширина, чтобы док не выглядел куцым
        int dockWidth = Math.max(200, totalContentWidth + 20);
        int dockX = (screenW - dockWidth) / 2;
        int dockY = screenH - DOCK_HEIGHT - DOCK_BOTTOM_MARGIN;

        // 1. Рисуем фон Дока (скругленный по краям - имитация)
        // Тень
        g.fill(dockX + 2, dockY + 2, dockX + dockWidth + 2, dockY + DOCK_HEIGHT + 2, 0x40000000);
        // Основа
        g.fill(dockX, dockY, dockX + dockWidth, dockY + DOCK_HEIGHT, DOCK_BG_COLOR);
        // Обводка (Border) сверху
        g.fill(dockX, dockY, dockX + dockWidth, dockY + 1, DOCK_BORDER_COLOR);

        int currentX = dockX + 10;
        int iconY = dockY + (DOCK_HEIGHT - ICON_SIZE) / 2;

        // 2. Кнопка Пуск (Логотип системы)
        startButtonArea = new ClickableArea(currentX, iconY, ICON_SIZE, ICON_SIZE);
        // Рисуем лого (оранжевый квадрат)
        g.fill(currentX, iconY, currentX + ICON_SIZE, iconY + ICON_SIZE, START_BTN_COLOR);
        // Простой символ "L" внутри
        g.drawCenteredString("L", currentX + ICON_SIZE/2, iconY + 8, 0xFFFFFFFF);

        currentX += ICON_SIZE + ICON_SPACING;

        // Разделитель (вертикальная полоска)
        g.fill(currentX, iconY + 4, currentX + 1, iconY + ICON_SIZE - 4, 0x40FFFFFF);
        currentX += ICON_SPACING;

        // 3. Иконки запущенных приложений
        dockIcons.clear();
        UUID activeAppId = windowManager.getActiveAppId();

        for (Map.Entry<UUID, WindowManager.AppInfo> entry : windowManager.getRunningApps().entrySet()) {
            String label = entry.getValue().path().substring(entry.getValue().path().lastIndexOf('/') + 1)
                    .replace(".jar", "").replace(".lua", "");

            ClickableArea area = new ClickableArea(currentX, iconY, ICON_SIZE, ICON_SIZE);
            dockIcons.add(new DockIcon(entry.getKey(), label, area));

            // Фон иконки (темный квадрат)
            g.fill(currentX, iconY, currentX + ICON_SIZE, iconY + ICON_SIZE, 0xFF444444);

            // Имя (первая буква)
            String iconLetter = label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();

            // Генерация цвета для иконки на основе имени
            int appColor = 0xFF000000 | (label.hashCode() & 0xFFFFFF);
            // Если цвет слишком темный, делаем его светлее
            if ((appColor & 0x00FFFFFF) < 0x202020) appColor = 0xFFCCCCCC;

            g.drawCenteredString(iconLetter, currentX + ICON_SIZE/2, iconY + 8, appColor);

            // Индикатор активности (белая точка под иконкой)
            if (entry.getKey().equals(activeAppId)) {
                g.fill(currentX + ICON_SIZE/2 - 2, iconY + ICON_SIZE - 4, currentX + ICON_SIZE/2 + 2, iconY + ICON_SIZE - 2, 0xFFFFFFFF);
            }

            currentX += ICON_SIZE + ICON_SPACING;
        }

        // 4. Кнопка "Домой" (справа)
        // Сдвигаем кнопку "Домой" в самый конец дока
        int homeX = dockX + dockWidth - ICON_SIZE - 10;
        homeButtonArea = new ClickableArea(homeX, iconY, ICON_SIZE, ICON_SIZE);

        // Рисуем кнопку Домой (темно-серый квадрат)
        g.fill(homeX, iconY, homeX + ICON_SIZE, iconY + ICON_SIZE, 0xFF333333);
        drawHomeIcon(g, homeX, iconY);

        // 5. Рендер Меню (если открыто)
        // Оно должно быть "над" панелью, поэтому рендерим последним
        if (systemMenu.isVisible()) {
            // Позиционируем меню над кнопкой Пуск (с небольшим отступом)
            int menuX = dockX; // Выравниваем по левому краю дока
            int menuY = dockY - systemMenu.getHeight() - 5;

            systemMenu.setPosition(menuX, menuY);
            systemMenu.render(g, mouseX, mouseY);
        }
    }

    private void drawHomeIcon(IKernelGraphics g, int x, int y) {
        int roofY = y + 8;  // Скорректировано под ICON_SIZE = 32
        int houseY = y + 16;
        // Крыша (треугольник) - упрощенная
        g.fill(x + 16, roofY, x + 18, roofY + 2, HOME_ICON_COLOR);      // Верхушка
        g.fill(x + 12, roofY + 4, x + 22, roofY + 6, HOME_ICON_COLOR);  // Середина крыши
        g.fill(x + 8, roofY + 8, x + 26, roofY + 10, HOME_ICON_COLOR);  // Низ крыши

        // Основание дома
        g.fill(x + 10, houseY, x + 24, y + 26, HOME_ICON_COLOR);
        // Дверь
        g.fill(x + 15, houseY + 4, x + 19, y + 26, 0xFF333333);
    }

    /**
     * Обрабатывает клики по кнопке "Пуск", "Домой" и иконкам приложений.
     */
    public boolean handleEvent(KernelEvent event) {
        if (windowManager == null) return false;

        // 1. Сначала даем шанс Меню обработать событие (оно перекрывает всё)
        if (systemMenu.isVisible()) {
            if (systemMenu.onEvent(event)) {
                // Если меню обработало событие (клик по кнопке или клик мимо), то всё.
                return true;
            }
        }

        if (!(event instanceof KernelEvent.MouseClicked mouseEvent)) {
            return false;
        }

        // 2. Проверяем клик по кнопке "Пуск"
        if (startButtonArea != null && startButtonArea.isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
            systemMenu.toggle();
            return true;
        }

        // 3. Проверяем кнопку "Домой"
        if (homeButtonArea != null && homeButtonArea.isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
            // При нажатии домой закрываем меню, если оно было открыто
            if (systemMenu.isVisible()) systemMenu.setVisible(false);

            LOGGER.info("Нажата кнопка 'Домой'.");
            windowManager.switchToApp(null); // Переход на рабочий стол
            return true;
        }

        // 4. Иконки приложений
        for (DockIcon icon : dockIcons) {
            if (icon.area().isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
                if (systemMenu.isVisible()) systemMenu.setVisible(false);

                windowManager.switchToApp(icon.appId());
                return true;
            }
        }

        // Клик по пустому месту В ДОКЕ не должен проваливаться на рабочий стол
        // Определяем зону дока грубо (низ экрана)
        int screenHeight = api.getGraphics().getHeight();
        if (mouseEvent.mouseY >= screenHeight - DOCK_HEIGHT - DOCK_BOTTOM_MARGIN) {
            // Если клик попал в зону дока, но не по кнопкам - просто поглощаем его
            return true;
        }

        return false;
    }

    public void shutdown() {
        LOGGER.info("NavigationBar shutting down...");
    }
}