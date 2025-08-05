package com.loracore.computer;

import org.luaj.vm2.LuaValue;

// Общий интерфейс для всех наших виртуальных файловых систем
public interface IFileSystem {
    boolean exists(String path);
    boolean isDirectory(String path);
    LuaValue read(String path); // LuaValue, чтобы легко вернуть nil
    boolean write(String path, String content);
    boolean makeDir(String path);
    String list(String path); // Возвращает JSON-строку
}