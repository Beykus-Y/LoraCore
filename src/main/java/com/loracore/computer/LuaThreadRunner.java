package com.loracore.computer;

import com.loracore.LoraCoreMod;
import com.loracore.api.ClientApi;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LuaThreadRunner implements Runnable {

    private final Globals globals;
    private final Terminal terminal;
    private final String bootScript;

    private final BlockingQueue<LuaValue[]> eventQueue = new LinkedBlockingQueue<>();
    private Thread workerThread;
    private LuaThread kernelCoroutine;

    // --- НОВЫЕ ПОЛЯ ---
    // Простая структура для хранения задачи на возобновление
    private record Resumable(LuaThread coroutine, Varargs value) {}
    // Потокобезопасная очередь для задач на возобновление
    private final BlockingQueue<Resumable> resumeQueue = new LinkedBlockingQueue<>();

    public LuaThreadRunner(Globals globals, Terminal terminal, String bootScript) {
        this.globals = globals;
        this.terminal = terminal;
        this.bootScript = bootScript;
    }

    public Globals getGlobals() {
        return this.globals;
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

                // --- ШАГ 1: СНАЧАЛА проверяем приоритетную очередь VFS-ответов ---
                Resumable toResume = resumeQueue.poll();

                if (toResume != null) {
                    // Если есть ответ от VFS, немедленно используем его
                    resumeArgs = toResume.value();
                } else {
                    // Если ответов нет, БЛОКИРУЕМСЯ в ожидании обычного события
                    // Используем take() для блокировки вместо poll() с задержкой,
                    // чтобы не пропустить VFS-ответ, пришедший во время ожидания.
                    // Обернем в poll с небольшим таймаутом, чтобы цикл мог проверить interrupted.
                    LuaValue[] event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (Thread.currentThread().isInterrupted()) break;

                    // Важная проверка: пока мы ждали событие, мог прийти VFS-ответ. Проверим еще раз!
                    toResume = resumeQueue.poll();
                    if (toResume != null) {
                        resumeArgs = toResume.value();
                    } else {
                        resumeArgs = (event != null) ? LuaValue.varargsOf(event) : LuaValue.NIL;
                    }
                }

                // --- ШАГ 2: ЕДИНСТВЕННАЯ точка возобновления ---
                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Resuming kernel with args: {}", resumeArgs);
                Varargs result = kernelCoroutine.resume(resumeArgs);
                LoraCoreMod.LOGGER.info("[LuaThreadRunner] Kernel resumed with status: {}, result: {}", result.checkboolean(1), result);

                if (!result.checkboolean(1)) {
                    throw new LuaError(result.optjstring(2, "Kernel error"));
                }
            }
            LoraCoreMod.LOGGER.info("[LuaThreadRunner] Thread loop finished.");
        } catch (InterruptedException e) {
            LoraCoreMod.LOGGER.info("Lua thread {} was interrupted.", Thread.currentThread().getName());
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LoraCoreMod.LOGGER.error("!!! LUA VIRTUAL MACHINE KERNEL PANIC !!!", t);
            String causeMessage = (t.getCause() != null) ? t.getCause().getMessage() : "No root cause";
            final String errorMessage = "LUA KERNEL PANIC:\n\n" + t.getMessage() + "\n\nCause: " + causeMessage;
            ClientApi.executeOnRenderThread(() -> this.terminal.showCrashScreen(errorMessage));
        }
    }
}