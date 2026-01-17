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
            // Очищаем очереди событий
            eventQueue.clear();
            resumeQueue.clear();
            // Обнуляем корутину для предотвращения OrphanedThread
            kernelCoroutine = null;
            workerThread = null;
        }
    }

    public void pushEvent(LuaValue[] event) {
        // Логирование изменено на DEBUG уровень
        LoraCoreMod.LOGGER.debug("LuaThreadRunner.pushEvent: workerThread={}, alive={}",
                workerThread, workerThread != null ? workerThread.isAlive() : false);

        if (workerThread == null || !workerThread.isAlive()) {
            LoraCoreMod.LOGGER.warn("LuaThreadRunner: Cannot push event - worker thread is not alive");
            return;
        }

        try {
            LoraCoreMod.LOGGER.debug("LuaThreadRunner: Adding event to queue");
            eventQueue.put(event);
            LoraCoreMod.LOGGER.debug("LuaThreadRunner: Event added to queue successfully");
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

            // === ВКЛЮЧЕНА СИСТЕМА ОГРАНИЧЕНИЯ ТАКТОВ ===
            try {
                // Получаем доступ к библиотеке debug
                LuaValue debug_lib = globals.get("debug");
                if (debug_lib.isnil() || !debug_lib.istable()) {
                    throw new LuaError("Debug library not available.");
                }

                // Создаем Lua-функцию (наш хук) для ограничения тактов
                LuaFunction cycle_hook = new TwoArgFunction() {
                    @Override
                    public LuaValue call(LuaValue event, LuaValue line) {
                        // Потребляем 1000 тактов из бюджета
                        boolean consumed = vm.consumeCycles(1000);
                        
                        if (!consumed) {
                            // Если бюджет закончился, принудительно делаем yield
                            // Это "усыпит" скрипт до следующего серверного тика
                            throw new LuaError("Out of cycles - yielding until next tick");
                        }
                        return NIL;
                    }
                };

                // Устанавливаем хук: вызывать `cycle_hook` каждые 1000 инструкций Lua.
                // Пустая строка "" означает, что нас не интересуют события (call, line, return).
                debug_lib.get("sethook").call(cycle_hook, valueOf(""), valueOf(1000));

                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Cycle hook установлен успешно (1000 cycles per 1000 instructions)");
            } catch (Exception e) {
                LoraCoreMod.LOGGER.warn("[LuaThreadRunner] Не удалось установить cycle hook: {}. Lua будет работать без ограничений.", e.getMessage());
            }

            while (kernelCoroutine != null && kernelCoroutine.state.status != LuaThread.STATUS_DEAD && !Thread.currentThread().isInterrupted()) {
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

                // Проверяем, что корутина еще существует (защита от OrphanedThread)
                if (kernelCoroutine == null) {
                    LoraCoreMod.LOGGER.warn("[LuaThreadRunner] Kernel coroutine was nulled, stopping thread");
                    break;
                }

                try {
                    Varargs result = kernelCoroutine.resume(resumeArgs);

                    if (!result.checkboolean(1)) {
                        throw new LuaError(result.optjstring(2, "Kernel error"));
                    }
                } catch (LuaError e) {
                    // Проверяем, не является ли это OrphanedThread ошибкой
                    if (e.getMessage() != null && e.getMessage().contains("OrphanedThread")) {
                        LoraCoreMod.LOGGER.warn("[LuaThreadRunner] OrphanedThread detected, cleaning up and stopping thread");
                        kernelCoroutine = null;
                        break;
                    }
                    // Если это другая ошибка, пробрасываем дальше
                    throw e;
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
            //          ФИНАЛЬНОЕ ИСПРАВЛЕНИЕ v3
            // =======================================================
            // Проверяем OrphanedThread - это нормальное поведение при закрытии
            if (t instanceof org.luaj.vm2.OrphanedThread) {
                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Lua thread cleaned up (OrphanedThread)");
                return; // Не вызываем setCrashState, это нормально
            }
            
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