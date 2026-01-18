package com.loracore.computer;

import java.util.List;

public interface IBlockingVFS {
    String readBlocking(String path);
    boolean existsBlocking(String path);
    boolean writeBlocking(String path, String content);
    boolean makeDirBlocking(String path);
    boolean isDirBlocking(String path);
    boolean deleteBlocking(String path);
    List<String> listBlocking(String path);
}
