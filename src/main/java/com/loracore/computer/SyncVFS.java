// Полностью исправленный файл: src/main/java/com/loracore/computer/SyncVFS.java
package com.loracore.computer;

import java.util.List;

public class SyncVFS implements IFileSystem {
    private final IBlockingVFS blockingVfs;
    public SyncVFS(IBlockingVFS blockingVfs) {
        this.blockingVfs = blockingVfs;
    }
    @Override
    public boolean exists(String path) { return blockingVfs.existsBlocking(path); }
    @Override
    public boolean isDirectory(String path) { return blockingVfs.isDirBlocking(path); }
    @Override
    public String read(String path) { return blockingVfs.readBlocking(path); }
    @Override
    public boolean write(String path, String content) { return blockingVfs.writeBlocking(path, content); }
    @Override
    public boolean makeDir(String path) { return blockingVfs.makeDirBlocking(path); }
    @Override
    public List<String> list(String path) { return blockingVfs.listBlocking(path); }
    @Override
    public boolean delete(String path) { return blockingVfs.deleteBlocking(path); }
    @Override
    public byte[] readBytes(String path) { return new byte[0]; }
    @Override
    public boolean writeBytes(String path, byte[] data) { return false; }
}
