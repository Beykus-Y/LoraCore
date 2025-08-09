package com.lora.tabletos.core;

import com.loracore.computer.kernel.*;
import com.lora.tabletos.state.KernelState;
import com.lora.tabletos.state.StateManager;
import com.lora.tabletos.ui.desktop.Desktop;
import com.lora.tabletos.ui.navigation.NavigationBar;
import com.lora.tabletos.ui.renderer.BootScreenRenderer;
import com.lora.tabletos.ui.window.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoraOSKernel - современное ядро-супервизор для LoraCore Tablet.
 * Реализует машину состояний для надежной загрузки, проверки системы
 * и передачи управления графической оболочке, написанной на Java.
 * Перехватывает любые сбои на уровне компонентов для предотвращения падения всей ОС.
 */
public class LoraOSKernel implements IKernel {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LoraOSKernel.class);
    
    // --- Управление состоянием ---
    private final StateManager stateManager;
    
    // --- Системные компоненты ---
    private Desktop desktop;
    private WindowManager windowManager;
    private NavigationBar navigationBar;
    
    // --- Утилиты и API ---
    private IKernelApi api;
    private IKernelGraphics graphics;
    private IKernelVfs vfs;
    private String statusMessage = "";
    private String panicMessage = "";
    
    public LoraOSKernel() {
        this.stateManager = new StateManager();
    }
    
    @Override
    public void onBoot(IKernelApi api) {
        LOGGER.info("LoraOS Kernel is booting...");
        this.api = api;
        this.graphics = api.getGraphics();
        this.vfs = api.getVfs();
        this.statusMessage = "Starting up...";
    }
    
    @Override
    public void onTick() {
        // Обрабатываем переходы состояний
        if (stateManager.processTransitions()) {
            // Переход произошел, продолжаем
        }
        
        stateManager.incrementTimer();
        
        // Если мы уже запустили асинхронную операцию, ничего не делаем
        if (stateManager.isBusy()) {
            return;
        }
        
        switch (stateManager.getCurrentState()) {
            case BOOTING:
                if (stateManager.getStateTimer() > 20) { // 1 секунда при 20 TPS
                    stateManager.setBusy(true);
                    stateManager.setNextState(KernelState.CHECKING_SYSTEM);
                    runSystemChecks();
                }
                break;
            case CHECKING_SYSTEM:
                // Ожидаем завершения асинхронной операции
                break;
            case INITIALIZING_UI:
                // Ожидаем завершения асинхронной операции
                break;
            case RUNNING:
                // Нормальная работа - ничего не делаем в onTick
                break;
            case BOOT_FAILED:
            case KERNEL_PANIC:
                // Критические состояния - ничего не делаем
                break;
        }
    }
    
    /**
     * Запускает менеджер проверки целостности системы.
     */
    private void runSystemChecks() {
        LOGGER.info("Starting system integrity checks...");
        SystemIntegrityManager integrityManager = new SystemIntegrityManager(this.vfs, status -> this.statusMessage = status);
        
        integrityManager.run().whenComplete((allChecksPassed, throwable) -> {
            if (throwable != null || !allChecksPassed) {
                LOGGER.error("System integrity check failed", throwable);
                this.statusMessage = "System check failed! See logs.";
                stateManager.setNextState(KernelState.BOOT_FAILED);
            } else {
                LOGGER.info("System integrity checks passed, initializing UI...");
                stateManager.setNextState(KernelState.INITIALIZING_UI);
                initializeUI();
            }
        });
    }
    
    /**
     * Инициализирует пользовательский интерфейс.
     */
    private void initializeUI() {
        stateManager.setBusy(true);
        this.statusMessage = "Loading Desktop...";
        
        try {
            this.windowManager = new WindowManager(api);
            this.desktop = new Desktop(api, this.windowManager);
            this.navigationBar = new NavigationBar(api);
            
            // Устанавливаем ссылку на WindowManager в NavigationBar
            this.navigationBar.setWindowManager(this.windowManager);
            
            desktop.initialize().whenComplete((success, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("UI initialization failed", throwable);
                    this.panicMessage = "UI Failed to load: " + throwable.getMessage();
                    stateManager.setNextState(KernelState.KERNEL_PANIC);
                } else if (success) {
                    LOGGER.info("UI initialization completed successfully");
                    stateManager.setNextState(KernelState.RUNNING);
                } else {
                    LOGGER.error("UI initialization returned false");
                    this.panicMessage = "UI Failed to load. Check logs.";
                    stateManager.setNextState(KernelState.KERNEL_PANIC);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to create UI components", e);
            this.panicMessage = "Failed to create UI components: " + e.getMessage();
            stateManager.setNextState(KernelState.KERNEL_PANIC);
        }
    }

    @Override
    public void onRender(int mouseX, int mouseY, float delta) {
        if (graphics == null) return;

        try {
            graphics.beginFrame();

            // Если система находится в рабочем состоянии
            if (stateManager.getCurrentState() == KernelState.RUNNING) {

                // 1. Рисуем фон рабочего стола (обои). Это основа для всего.
                desktop.render(graphics, mouseX, mouseY, delta);

                // 2. Определяем область для контента приложения
                final int contentAreaWidth = 480;
                final int contentAreaHeight = 240; // 270 (экран) - 30 (панель)

                // 3. Рендерим активное приложение или ничего, если его нет
                if (windowManager.hasActiveApp()) {
                    graphics.pushMatrix();

                    // 3.1. Ограничиваем область рисования зоной контента
                    graphics.enableScissor(0, 0, contentAreaWidth, contentAreaHeight);

                    // 3.2. Масштабируем координаты мыши для приложения
                    // Приложениям не нужно знать о панели навигации.
                    // Для них мир начинается в (0, 0) и заканчивается в (480, 240).
                    // Мы передаем им уже скорректированные координаты.
                    int appMouseX = mouseX;
                    int appMouseY = mouseY;

                    // 3.3. Вызываем рендер активного приложения
                    windowManager.render(graphics, appMouseX, appMouseY, delta);

                    // 3.4. Снимаем ограничение и восстанавливаем матрицу
                    graphics.disableScissor();
                    graphics.popMatrix();
                }
                // ВАЖНО: ветки 'else' здесь нет. Рабочий стол (обои) уже нарисован.
                // Если нет активного приложения, мы просто видим обои.

                // 4. Рисуем панель навигации поверх всего остального.
                // Она находится вне зоны отсечения (scissor) и всегда видна.
                navigationBar.render(graphics, mouseX, mouseY, delta);

            } else {
                // Во всех остальных состояниях (загрузка, сбой) рисуем системный экран
                BootScreenRenderer.render(graphics, stateManager.getCurrentState(), statusMessage, panicMessage);
            }

        } catch (Exception e) {
            // Логика обработки критических ошибок остается неизменной
            LOGGER.error("KERNEL PANIC! Unhandled exception in render loop.", e);
            stateManager.setNextState(KernelState.KERNEL_PANIC);
            this.panicMessage = e.getMessage();
            // В случае паники мы тоже рисуем системный экран
            BootScreenRenderer.render(graphics, KernelState.KERNEL_PANIC, statusMessage, panicMessage);
        } finally {
            graphics.endFrame();
        }
    }
    
    @Override
    public void onEvent(KernelEvent event) {
        // События обрабатываются только в рабочем состоянии
        if (stateManager.getCurrentState() != KernelState.RUNNING) return;
        
        try {
            // Передаем событие компонентам по порядку приоритета
            // Сначала проверяем навигационную панель (кнопка "Домой")
            if (navigationBar.handleEvent(event)) {
                return; // Если навигационная панель обработала событие, выходим
            }
            
            // Затем проверяем активное приложение
            if (windowManager.handleEvent(event)) {
                return; // Если приложение обработало событие, выходим
            }
            
            // Если нет активного приложения, проверяем рабочий стол
            if (!windowManager.hasActiveApp() && desktop.handleEvent(event)) {
                return; // Если рабочий стол обработал событие, выходим
            }
        } catch (Exception e) {
            LOGGER.error("KERNEL PANIC! Unhandled exception in event loop.", e);
            stateManager.setNextState(KernelState.KERNEL_PANIC);
            this.panicMessage = e.getMessage();
        }
    }
    
    @Override
    public void onShutdown() {
        LOGGER.info("LoraOS Kernel is shutting down...");
        if (windowManager != null) {
            windowManager.shutdown();
        }
        if (navigationBar != null) {
            navigationBar.shutdown();
        }
    }
    
    @Override
    public Object getState() {
        return stateManager.getCurrentState().name();
    }
    
    @Override
    public Map<String, Boolean> getCheckResults() {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        results.put("kernel_running", stateManager.getCurrentState() == KernelState.RUNNING);
        results.put("system_checks_passed", stateManager.getCurrentState() != KernelState.BOOT_FAILED);
        results.put("ui_initialized", stateManager.getCurrentState() == KernelState.RUNNING);
        results.put("no_panic", stateManager.getCurrentState() != KernelState.KERNEL_PANIC);
        return results;
    }
}
