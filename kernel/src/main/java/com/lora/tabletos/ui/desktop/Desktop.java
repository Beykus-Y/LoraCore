package com.lora.tabletos.ui.desktop;

import com.loracore.computer.kernel.IKernelApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.IKernelVfs;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.ui.window.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Управляет рабочим столом и иконками приложений.
 */
public class Desktop {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Desktop.class);
    
    private final IKernelApi api;
    private final WindowManager windowManager;
    private final IKernelVfs vfs;
    private final List<DesktopIcon> icons = new ArrayList<>();
    
    // Константы для расположения иконок
    private static final int ICON_START_X = 20;
    private static final int ICON_START_Y = 20;
    private static final int ICON_SPACING = 20;
    private static final int BACKGROUND_COLOR = 0xFF2D3E50; // Темно-сине-серый
    
    public Desktop(IKernelApi api, WindowManager windowManager) {
        this.api = api;
        this.windowManager = windowManager;
        this.vfs = api.getVfs();
    }
    
    /**
     * Асинхронно инициализирует рабочий стол: загружает иконки и размещает их.
     * 
     * @return Future, который вернет `true` при успехе или `false` при сбое.
     */
    public CompletableFuture<Boolean> initialize() {
        LOGGER.info("Desktop initialization started: loading icons...");
        String appsPath = "/home/user/apps";
        
        return vfs.list(appsPath).thenApply(fileList -> {
            if (fileList == null) {
                LOGGER.warn("App directory '{}' does not exist or is empty. No icons to load.", appsPath);
                return true; // Это не ошибка, просто нет приложений
            }
            
            // Очищаем старый список на случай перезагрузки UI
            icons.clear();
            for (String fileName : fileList) {
                if (fileName.endsWith(".lua")) {
                    icons.add(new DesktopIcon(fileName, appsPath));
                }
            }
            LOGGER.info("Found {} app(s). Laying out icons.", icons.size());
            layoutIcons(); // Размещаем иконки на экране
            return true; // Инициализация успешна
            
        }).exceptionally(throwable -> {
            LOGGER.error("Desktop initialization failed: Could not list apps in '{}'.", appsPath, throwable);
            return false; // Инициализация провалена
        });
    }
    
    /**
     * Рассчитывает и задает X/Y координаты для каждой иконки в виде сетки.
     */
    private void layoutIcons() {
        int currentX = ICON_START_X;
        int currentY = ICON_START_Y;
        int screenWidth = 480; // Используем стандартный размер экрана
        
        for (DesktopIcon icon : icons) {
            icon.setPosition(currentX, currentY);
            
            // Сдвигаем курсор для следующей иконки
            currentX += icon.getWidth() + ICON_SPACING;
            
            // Если вышли за правую границу экрана, переносим курсор на новую строку
            if (currentX + icon.getWidth() > screenWidth) {
                currentX = ICON_START_X;
                currentY += icon.getHeight() + ICON_SPACING + 10; // +10 пикселей для текста под иконкой
            }
        }
    }
    
    /**
     * Вызывается каждый кадр для отрисовки рабочего стола и всех иконок.
     */
    public void render(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        // 1. Рисуем фон
        g.fill(0, 0, g.getWidth(), g.getHeight(), BACKGROUND_COLOR);
        
        // 2. Рисуем каждую иконку
        for (DesktopIcon icon : icons) {
            icon.render(g);
        }
    }
    
    /**
     * Обрабатывает события ввода, в основном клики по иконкам.
     * 
     * @return `true` если событие было обработано, иначе `false`.
     */
    public boolean handleEvent(KernelEvent event) {
        if (event instanceof KernelEvent.MouseClicked mouseEvent) {
            // Проверяем, не был ли клик по какой-нибудь иконке
            for (DesktopIcon icon : icons) {
                if (icon.isClicked((int)mouseEvent.mouseX, (int)mouseEvent.mouseY)) {
                    LOGGER.info("Icon '{}' clicked. Launching script: {}", icon.getLabel(), icon.getScriptPath());
                    windowManager.launchApp(icon.getScriptPath());
                    return true; // Сообщаем ядру, что мы обработали это событие
                }
            }
        }
        return false; // Мы не обрабатываем это событие, оно может быть передано другим компонентам
    }
}
