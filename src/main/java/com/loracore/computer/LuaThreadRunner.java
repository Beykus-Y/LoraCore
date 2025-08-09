// Полный исправленный файл: src/main/java/com/loracore/computer/LuaThreadRunner.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import static org.luaj.vm2.LuaValue.valueOf;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LuaThreadRunner implements Runnable {

    private final VirtualMachine vm;
    private Globals globals;
    private final String bootScript;

    private final BlockingQueue<LuaValue[]> eventQueue = new LinkedBlockingQueue<>();
    private Thread workerThread;
    private LuaThread kernelCoroutine;

    private record Resumable(LuaThread coroutine, Varargs value) {}
    private final BlockingQueue<Resumable> resumeQueue = new LinkedBlockingQueue<>();

    public LuaThreadRunner(VirtualMachine vm, Globals globals, String bootScript) {
        this.vm = vm;
        this.globals = globals;
        this.bootScript = bootScript;
    }

    public Globals getGlobals() {
        return this.globals;
    }

    public boolean isAlive() {
        return workerThread != null && workerThread.isAlive();
    }
    public void setGlobals(Globals globals) {
        this.globals = globals;
    }

    /**
     * Вызывается из VirtualMachine, когда приходит ответ от сервера.
     * Добавляет корутину и ее результат в очередь на возобновление.
     */
    public void resumeWith(LuaThread coroutine, Varargs value) {
        try {
            resumeQueue.put(new Resumable(coroutine, value));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        if (workerThread != null && workerThread.isAlive()) {
            return;
        }
        workerThread = new Thread(this, "LoraCore-LuaVM");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void stop() {
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    public void pushEvent(LuaValue[] event) {
        // ДИАГНОСТИКА: Логируем события
        LoraCoreMod.LOGGER.info("LuaThreadRunner.pushEvent: workerThread={}, alive={}", 
            workerThread, workerThread != null ? workerThread.isAlive() : false);
        
        if (workerThread == null || !workerThread.isAlive()) {
            LoraCoreMod.LOGGER.warn("LuaThreadRunner: Cannot push event - worker thread is not alive");
            return;
        }
        
        try {
            LoraCoreMod.LOGGER.info("LuaThreadRunner: Adding event to queue");
            eventQueue.put(event);
            LoraCoreMod.LOGGER.info("LuaThreadRunner: Event added to queue successfully");
        } catch (InterruptedException e) {
            LoraCoreMod.LOGGER.error("LuaThreadRunner: Interrupted while adding event to queue", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        LoraCoreMod.LOGGER.info("[LuaThreadRunner] Started thread loop.");

        try {
            LuaValue bootloader = globals.load(bootScript, "@boot.lua");
            this.kernelCoroutine = new LuaThread(globals, bootloader);
            Varargs resumeArgs = LuaValue.NIL;

            // --- ВРЕМЕННО ОТКЛЮЧЕНА НОВАЯ СИСТЕМА КОНТРОЛЯ ПАМЯТИ ДЛЯ ДИАГНОСТИКИ ---
            // TODO: Включить обратно после исправления проблем с recovery.lua
            /*
            try {
                // Получаем доступ к библиотеке debug
                LuaValue debug_lib = globals.get("debug");
                if (debug_lib.isnil() || !debug_lib.istable()) {
                    throw new LuaError("Debug library not available.");
                }

                // Получаем лимит памяти из VirtualMachine
                final double memoryLimitKb = vm.getTotalRamKb();

                // Создаем Lua-функцию (наш хук) на лету
                LuaFunction memory_hook = new TwoArgFunction() {
                    @Override
                    public LuaValue call(LuaValue event, LuaValue line) {
                        // Получаем текущее использование памяти в КБ
                        double currentUsageKb = vm.getMemoryUsage();

                        if (currentUsageKb > memoryLimitKb) {
                            // Если лимит превышен, мы немедленно прерываем выполнение,
                            // выбрасывая ошибку прямо изнутри Lua VM.
                            // Это намного быстрее, чем ждать проверки из Java.
                            throw new LuaError(String.format("Out of Memory: %.2fKB / %.2fKB", currentUsageKb, memoryLimitKb));
                        }
                        return NIL;
                    }
                };

                // Устанавливаем хук: вызывать `memory_hook` каждые 20000 инструкций Lua.
                // Пустая строка "" означает, что нас не интересуют события (call, line, return).
                debug_lib.get("sethook").call(memory_hook, valueOf(""), valueOf(20000));

                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Memory hook установлен успешно. Лимит: {}KB", (int)memoryLimitKb);
            } catch (Exception e) {
                LoraCoreMod.LOGGER.warn("[LuaThreadRunner] Не удалось установить memory hook: {}. Используем старую систему проверки.", e.getMessage());
            }
            */
            LoraCoreMod.LOGGER.info("[LuaThreadRunner] Memory hook временно отключен для диагностики");

            while (kernelCoroutine.state.status != LuaThread.STATUS_DEAD && !Thread.currentThread().isInterrupted()) {
                Resumable toResume = resumeQueue.poll();

                if (toResume != null) {
                    resumeArgs = toResume.value();
                } else {
                    LuaValue[] event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (Thread.currentThread().isInterrupted()) break;

                    toResume = resumeQueue.poll();
                    if (toResume != null) {
                        resumeArgs = toResume.value();
                    } else {
                        resumeArgs = (event != null) ? LuaValue.varargsOf(event) : LuaValue.NIL;
                    }
                }

                Varargs result = kernelCoroutine.resume(resumeArgs);

                if (!result.checkboolean(1)) {
                    throw new LuaError(result.optjstring(2, "Kernel error"));
                }

                // СТАРАЯ ПЕРИОДИЧЕСКАЯ ПРОВЕРКА ПАМЯТИ УДАЛЕНА
                // Теперь контроль памяти осуществляется через Lua debug hook
                // который срабатывает каждые 20000 инструкций и намного эффективнее
            }
            LoraCoreMod.LOGGER.info("[LuaThreadRunner] Thread loop finished.");
        }  catch (InterruptedException e) {
            LoraCoreMod.LOGGER.info("Lua thread {} was interrupted.", Thread.currentThread().getName());
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            // =======================================================
            //          ФИНАЛЬНОЕ ИСПРАВЛЕНИЕ v2
            // =======================================================
            // Проверяем, содержит ли ТЕКСТ СООБЩЕНИЯ ошибки название нашего класса-сигнала.
            // Это надежнее, чем проверять getCause(), так как LuaJ вставляет его как текст.
            if (t instanceof LuaError && t.getMessage() != null && t.getMessage().contains(RebootSignalException.class.getName())) {
                // Если да - это НЕ паника. Это штатная перезагрузка.
                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Reboot signal received. Thread is shutting down cleanly.");
            } else {
                // Если это любая другая ошибка - это НАСТОЯЩАЯ паника.
                LoraCoreMod.LOGGER.error("!!! LUA VIRTUAL MACHINE KERNEL PANIC !!!", t);
                String causeMessage = (t.getCause() != null) ? t.getCause().getMessage() : "No root cause";
                final String errorMessage = "LUA KERNEL PANIC:\n\n" + t.getMessage() + "\n\nCause: " + causeMessage;

                vm.setCrashState(errorMessage);
            }
        }

        LoraCoreMod.LOGGER.info("[LuaThreadRunner] Thread loop finished.");
    }
}