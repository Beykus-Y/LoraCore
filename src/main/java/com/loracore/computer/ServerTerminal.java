// Расположение: src/main/java/com/loracore/computer/ServerTerminal.java
package com.loracore.computer;

import com.loracore.service.TabletRenderService;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Серверная реализация интерфейса Terminal.
 * Не рисует на экране, а вместо этого изменяет ServerScreenState
 * через TabletRenderService.
 */
public class ServerTerminal implements Terminal {

    private final UUID tabletUuid;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>(1);

    public ServerTerminal(UUID tabletUuid) {
        this.tabletUuid = tabletUuid;
    }

    private ServerScreenState getScreenState() {
        return TabletScreenManager.getInstance().getScreen(this.tabletUuid);
    }

    @Override
    public void print(String text) {
        // Логика печати текста будет реализована позже, когда мы адаптируем
        // TabletRenderService для работы с курсором. Пока что это заглушка.
        ServerScreenState screen = getScreenState();
        if (screen != null) {
            // TODO: Add cursor logic to TabletRenderService
            screen.markDirty();
        }
    }

    @Override
    public void clear() {
        ServerScreenState screen = getScreenState();
        if (screen != null) {
            TabletRenderService.getInstance().processFill(screen, 0, 0, 480, 270, 0x1E1E1E); // Fill with black
        }
    }

    @Override
    public String read() {
        try {
            // Этот вызов заблокирует поток Lua VM до тех пор,
            // пока от клиента не придет пакет с вводом.
            return this.inputQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Этот метод будет вызываться из сетевого обработчика,
     * когда от клиента придет пакет с введенной строкой.
     */
    public void receiveInput(String input) {
        this.inputQueue.offer(input);
    }

    // --- Остальные методы интерфейса пока оставляем заглушками ---

    @Override public void setCursorPos(int x, int y) { /* TODO */ }
    @Override public void clearLine() { /* TODO */ }
    @Override public void setTextColor(int color) { /* TODO */ }
    @Override public void setBackgroundColor(int color) { /* TODO */ }
    @Override public int[] getCursorPos() { return new int[]{1, 1}; }
    @Override public int[] getSize() { return new int[]{80, 25}; } // Стандартные размеры
    @Override public void reboot() { /* TODO */ }
    @Override public void showCrashScreen(String message) { /* TODO */ }
    @Override public void setCursorBlink(boolean enabled) { /* TODO */ }
}