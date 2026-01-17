package lora.emulator.runtime;

import lora.emulator.cpu.VirtualCpu;

/**
 * Автономный раннер для CPU.
 * Используй его только если тебе плевать на синхронизацию с GPU и клавиатурой.
 */
public class CpuRunner implements Runnable {
    private final VirtualCpu cpu;
    private volatile boolean running = true;
    private final long freqHz;
    private final long nsPerCycle;

    public CpuRunner(VirtualCpu cpu, long targetFreq) {
        this.cpu = cpu;
        this.freqHz = targetFreq;
        // Обновляем частоту в самом CPU (поле теперь называется freqHz)
        this.cpu.freqHz = (int) targetFreq;

        // Считаем наносекунды на такт в целых числах
        this.nsPerCycle = targetFreq > 0 ? 1_000_000_000L / targetFreq : 1000;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        long accumulator = 0; // Наносекунды — это LONG, а не double!

        System.out.printf("[CPU] Independent runner started at %d Hz\n", freqHz);

        while (running) {
            long now = System.nanoTime();
            long delta = now - lastTime;
            lastTime = now;

            // Защита от "скачков" времени (например, если комп лаганул)
            if (delta > 100_000_000L) delta = nsPerCycle;

            accumulator += delta;

            // Исполняем инструкции
            while (accumulator >= nsPerCycle) {
                // step() теперь всегда возвращает 1 такт в твоей реализации
                cpu.step();
                accumulator -= nsPerCycle;
            }

            // Если эмуляция идет слишком быстро — даем хост-машине подышать
            if (freqHz > 10000) {
                // На высоких частотах sleep(1) убьет производительность.
                // Просто намекаем планировщику ОС.
                Thread.yield();
            } else {
                try {
                    // На низких частотах можно и поспать
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void stop() {
        running = false;
    }
}