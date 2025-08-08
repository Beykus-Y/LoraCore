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
            
            // Если система в рабочем состоянии, отрисовываем UI
            if (stateManager.getCurrentState() == KernelState.RUNNING) {
                // Рисуем рабочий стол
                desktop.render(graphics, mouseX, mouseY, delta);
                windowManager.render(graphics, mouseX, mouseY, delta);
                navigationBar.render(graphics, mouseX, mouseY, delta);
            } else {
                // Во всех остальных случаях (загрузка, сбой) рисуем системный экран
                BootScreenRenderer.render(graphics, stateManager.getCurrentState(), statusMessage, panicMessage);
            }
        } catch (Exception e) {
            // Ловим любой сбой в компонентах, логируем и переходим в состояние паники
            LOGGER.error("KERNEL PANIC! Unhandled exception in render loop.", e);
            stateManager.setNextState(KernelState.KERNEL_PANIC);
            this.panicMessage = e.getMessage();
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
            if (navigationBar.handleEvent(event) || windowManager.handleEvent(event) || desktop.handleEvent(event)) {
                return; // Если кто-то обработал, выходим
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
