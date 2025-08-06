// Полный исправленный файл: src/main/java/com/loracore/computer/LuaThreadRunner.java
package com.loracore.computer;

import com.loracore.LoraCoreMod;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LuaThreadRunner implements Runnable {

    private final VirtualMachine vm;
    private final Globals globals;
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
        if (workerThread == null || !workerThread.isAlive()) return;
        try {
            eventQueue.put(event);
        } catch (InterruptedException e) {
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