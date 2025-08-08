// Полный исправленный файл: src/main/java/com/loracore/computer/api/FsAPI.java
package com.loracore.computer.api;

import com.loracore.computer.IAsyncVFS;
import com.loracore.computer.LuaThreadRunner;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class FsAPI extends LuaTable {

    public FsAPI(LuaThreadRunner runner, IAsyncVFS vfs) {
        // Конструктор теперь просто создает обертки, не обращаясь к Globals
        set("exists", createAsyncWrapper(runner, args -> vfs.existsAsync(args[0].checkjstring())));
        set("read", createAsyncWrapper(runner, args -> vfs.readAsync(args[0].checkjstring())));
        set("isDir", createAsyncWrapper(runner, args -> vfs.isDirectoryAsync(args[0].checkjstring())));
        set("list", createAsyncWrapper(runner, args -> vfs.listAsync(args[0].checkjstring())));
        set("makeDir", createAsyncWrapper(runner, args -> vfs.makeDirAsync(args[0].checkjstring())));
        set("write", createAsyncWrapper(runner, args -> vfs.writeAsync(args[0].checkjstring(), args[1].checkjstring())));
    }

    private static VarArgFunction createAsyncWrapper(LuaThreadRunner runner, Function<LuaValue[], CompletableFuture<LuaValue>> apiCall) {
        return new VarArgFunction() {
            private LuaValue coroutine_yield;

            @Override
            public Varargs invoke(Varargs args) {
                // ИСПРАВЛЕНИЕ: Получаем Globals и Thread В МОМЕНТ ВЫЗОВА, когда они гарантированно существуют
                Globals globals = runner.getGlobals();
                if (globals == null) {
                    throw new LuaError("Virtual machine globals are not initialized yet.");
                }
                LuaThread thread = globals.running;
                if (thread == null) {
                    throw new LuaError("Cannot call async function outside of a running coroutine.");
                }

                if (coroutine_yield == null) {
                    coroutine_yield = globals.get("coroutine").get("yield");
                }

                LuaValue[] luaArgs = new LuaValue[args.narg()];
                for (int i = 0; i < luaArgs.length; i++) {
                    luaArgs[i] = args.arg(i + 1);
                }

                apiCall.apply(luaArgs).whenComplete((result, error) -> {
                    if (error != null) {
                        runner.resumeWith(thread, varargsOf(NIL, valueOf(error.getMessage())));
                    } else {
                        runner.resumeWith(thread, result);
                    }
                });

                return coroutine_yield.invoke(NONE);
            }
        };
    }
}