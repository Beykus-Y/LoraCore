package com.lora.tabletos.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Управляет переходами между состояниями ядра.
 */
public class StateManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StateManager.class);
    
    private volatile KernelState currentState = KernelState.BOOTING;
    private volatile KernelState nextState = null;
    private boolean isBusy = false;
    private int stateTimer = 0;
    
    /**
     * Возвращает текущее состояние.
     */
    public KernelState getCurrentState() {
        return currentState;
    }
    
    /**
     * Возвращает следующее состояние.
     */
    public KernelState getNextState() {
        return nextState;
    }
    
    /**
     * Проверяет, занята ли система.
     */
    public boolean isBusy() {
        return isBusy;
    }
    
    /**
     * Устанавливает флаг занятости.
     */
    public void setBusy(boolean busy) {
        this.isBusy = busy;
    }
    
    /**
     * Возвращает таймер состояния.
     */
    public int getStateTimer() {
        return stateTimer;
    }
    
    /**
     * Увеличивает таймер состояния.
     */
    public void incrementTimer() {
        stateTimer++;
    }
    
    /**
     * Сбрасывает таймер состояния.
     */
    public void resetTimer() {
        stateTimer = 0;
    }
    
    /**
     * Устанавливает следующее состояние.
     */
    public void setNextState(KernelState nextState) {
        this.nextState = nextState;
    }
    
    /**
     * Обрабатывает переходы состояний.
     * 
     * @return true, если произошел переход
     */
    public boolean processTransitions() {
        if (nextState != null) {
            LOGGER.info("Transitioning from state {} to {}", currentState, nextState);
            currentState = nextState;
            nextState = null;
            stateTimer = 0;
            isBusy = false;
            return true;
        }
        return false;
    }
    
    /**
     * Проверяет, нужно ли переходить в следующее состояние.
     */
    public boolean shouldTransition() {
        return nextState != null;
    }
    
    /**
     * Проверяет, является ли текущее состояние состоянием загрузки.
     */
    public boolean isBootState() {
        return currentState.isBootState();
    }
    
    /**
     * Проверяет, является ли текущее состояние критическим.
     */
    public boolean isCritical() {
        return currentState.isCritical();
    }
}
