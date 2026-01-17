package lora.emulator.runtime;

import lora.emulator.LoraComputer;
import java.util.concurrent.locks.LockSupport;

public class SystemRunner implements Runnable {
    private final LoraComputer computer;
    private volatile boolean running = true;

    private final int targetHz;
    private final long nsPerTick;

    public SystemRunner(LoraComputer computer, int targetHz) {
        this.computer = computer;
        this.targetHz = targetHz;
        // Защита от деления на ноль, если какой-то гений поставит 0 Гц
        this.nsPerTick = targetHz > 0 ? 1_000_000_000L / targetHz : 1_000_000_000L;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        long accumulator = 0;

        // --- ЛИНУС: ДОБАВЛЕН TRY-CATCH ---
        try {
            while (running) {
                long now = System.nanoTime();
                long elapsed = now - lastTime;
                lastTime = now;

                accumulator += elapsed;

                if (accumulator > 100_000_000L) {
                    accumulator = nsPerTick;
                }

                while (accumulator >= nsPerTick) {
                    // Выполняем шаг CPU
                    int cycles = computer.getCpu().step();

                    // Тикаем периферию синхронно
                    computer.tickPeripherals(cycles);

                    accumulator -= (cycles * nsPerTick);
                }

                if (targetHz > 1000) {
                    Thread.yield();
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            // ВОТ ОНО! Если эмулятор падает, мы увидим почему.
            System.err.println("\n[CRITICAL] SystemRunner CRASHED!");
            t.printStackTrace();
            running = false;
        }
    }

    public void stop() {
        running = false;
    }
}