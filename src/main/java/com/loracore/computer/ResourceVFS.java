// Файл: src/main/java/com/loracore/computer/ResourceVFS.java
package com.loracore.computer;

import com.google.gson.Gson;
import com.loracore.LoraCoreMod;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ResourceVFS implements IFileSystem {

    private final ResourceManager resourceManager;
    private final String resourceRoot; // e.g., "os"
    private static final Gson GSON = new Gson();

    public ResourceVFS(ResourceManager resourceManager, String root) {
        this.resourceManager = resourceManager;
        this.resourceRoot = root;
    }

    private Identifier toIdentifier(String path) {
        String finalPath = resourceRoot + (path.startsWith("/") ? path : "/" + path);
        return new Identifier(LoraCoreMod.MOD_ID, finalPath);
    }

    @Override
    public boolean exists(String path) {
        return resourceManager.getResource(toIdentifier(path)).isPresent();
    }

    @Override
    public boolean isDirectory(String path) {
        return !resourceManager.findResources(resourceRoot + path, p -> true).isEmpty();
    }

    @Override
    public String read(String path) throws IOException {
        byte[] bytes = readBytes(path);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    @Override
    public java.util.List<String> list(String path) throws IOException {
        return resourceManager.findResources(resourceRoot + path, p -> true)
                .keySet()
                .stream()
                .map(id -> id.getPath().substring(id.getPath().lastIndexOf('/') + 1))
                .collect(Collectors.toList());
    }

    // --- НОВЫЕ МЕТОДЫ-ЗАГЛУШКИ ДЛЯ READ-ONLY СИСТЕМЫ ---

    @Override
    public boolean write(String path, String content) {
        // Нельзя записывать в ресурсы мода
        return false;
    }

    @Override
    public boolean makeDir(String path) {
        // Нельзя создавать папки в ресурсах мода
        return false;
    }

    @Override
    public boolean delete(String path) {
        // Нельзя удалять из ресурсов мода
        return false;
    }

    @Override
    public boolean writeBytes(String path, byte[] data) {
        // Нельзя записывать в ресурсы мода
        return false;
    }

    // --- НОВЫЙ РЕАЛИЗОВАННЫЙ МЕТОД ---

    @Override
    public byte[] readBytes(String path) throws IOException {
        return resourceManager.getResource(toIdentifier(path))
                .orElseThrow(() -> new IOException("Resource not found: " + path))
                .getInputStream()
                .readAllBytes();
    }
}
