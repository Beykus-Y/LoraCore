// Файл: src/main/java/com/loracore/computer/api/ColorsAPI.java
package com.loracore.computer.api;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Предоставляет Lua-скриптам доступ к стандартным 16-ти цветам Minecraft.
 * В Lua будет доступна как глобальная таблица 'colors'.
 */
public class ColorsAPI extends LuaTable {

    public ColorsAPI() {
        // Стандартные 16 цветов, используемых в ComputerCraft
        set("white",       LuaValue.valueOf(0xF0F0F0));
        set("orange",      LuaValue.valueOf(0xF2B233));
        set("magenta",     LuaValue.valueOf(0xE57FD8));
        set("lightBlue",   LuaValue.valueOf(0x99B2F2));
        set("yellow",      LuaValue.valueOf(0xDEDE6C));
        set("lime",        LuaValue.valueOf(0x7FCC19));
        set("pink",        LuaValue.valueOf(0xF2B2CC));
        set("gray",        LuaValue.valueOf(0x4C4C4C));
        set("lightGray",   LuaValue.valueOf(0x999999));
        set("cyan",        LuaValue.valueOf(0x4C99B2));
        set("purple",      LuaValue.valueOf(0xB266E5));
        set("blue",        LuaValue.valueOf(0x3366CC));
        set("brown",       LuaValue.valueOf(0x7F664C));
        set("green",       LuaValue.valueOf(0x57A64E));
        set("red",         LuaValue.valueOf(0xCC4C4C));
        set("black",       LuaValue.valueOf(0x1E1E1E));
    }
}