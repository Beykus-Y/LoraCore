package com.loracore.computer;

import java.io.IOException;
import java.util.List;

public interface IFileSystem {
    boolean exists(String path);
    boolean isDirectory(String path);
    String read(String path) throws IOException;
    boolean write(String path, String content) throws IOException;
    boolean makeDir(String path) throws IOException;
    List<String> list(String path) throws IOException;
    boolean delete(String path) throws IOException;
    byte[] readBytes(String path) throws IOException;
    boolean writeBytes(String path, byte[] data) throws IOException;
}
