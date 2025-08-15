package com.loracore.computer;

import org.luaj.vm2.LuaValue;

import java.io.IOException;

// Общий интерфейс для всех наших виртуальных файловых систем
public interface IFileSystem {
    boolean exists(String path);
    boolean isDirectory(String path);
    LuaValue read(String path);
    boolean write(String path, String content);
    boolean makeDir(String path);
    String list(String path);
    boolean delete(String path);
    byte[] readBytes(String path) throws IOException;
    boolean writeBytes(String path, byte[] data);
}