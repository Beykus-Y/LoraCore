package com.loracore.computer.api;

import com.loracore.computer.Terminal;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

public class DebugAPI extends LuaTable {
    private final Terminal terminal;

    public DebugAPI(Terminal terminal) {
        this.terminal = terminal;
        set("printError", new printError());
    }

    private class printError extends OneArgFunction {
        @Override
        public LuaValue call(LuaValue arg) {
            if (!arg.isnil()) {
                // Выводим текст красным цветом для наглядности
                // (Предполагается, что ваш Terminal сможет это обработать, добавим эту логику)
                terminal.print("\n[ERROR] " + arg.tojstring() + "\n");
            }
            return NIL;
        }
    }
}