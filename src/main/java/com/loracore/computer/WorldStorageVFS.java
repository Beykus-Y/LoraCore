package com.loracore.computer;

import com.google.gson.Gson;
import com.loracore.LoraCoreMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Base64;

public class WorldStorageVFS implements IFileSystem {
    private final Path rootDirectory;
    private final UUID fsUuid;
    private static final Gson GSON = new Gson();

    public WorldStorageVFS(Path worldRoot, UUID fsUuid) {
        // ИЗМЕНЕНИЕ: Убрали создание папок из конструктора.
        // Он теперь просто запоминает пути.
        this.rootDirectory = worldRoot.resolve("loracore_vfs");
        this.fsUuid = fsUuid;
    }

    private Path getDeviceRoot() {
        // Этот метод больше не создает папки и не выбрасывает IOException
        return rootDirectory.resolve(fsUuid.toString());
    }

    public Path getValidatedPath(String relativePath) throws IOException {
        // Убираем потенциальную точку из базового пути СРАЗУ
        Path deviceRoot = getDeviceRoot().normalize();

        String sanitizedRelativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;

        // Нормализуем целевой путь
        Path targetPath = deviceRoot.resolve(sanitizedRelativePath).normalize();

        // Теперь сравнение должно работать, так как оба пути очищены от "."
        if (!targetPath.startsWith(deviceRoot)) {
            LoraCoreMod.LOGGER.warn("[VFS] Path traversal attempt! Base: [{}], Target: [{}]", deviceRoot, targetPath);
            throw new IOException("Path traversal attempt detected!");
        }

        return targetPath;
    }

    @Override
    public boolean exists(String path) {
        try {
            return Files.exists(getValidatedPath(path));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String read(String path) throws IOException {
        Path target = getValidatedPath(path);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return null;
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    @Override
    public boolean write(String path, String content) {
        try {
            Path target = getValidatedPath(path);
            // ИЗМЕНЕНИЕ: Логика создания папки-родителя перенесена сюда.
            // Она сработает только при реальной необходимости записи.
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("[VFS] Failed to write to {}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean makeDir(String path) {
        try {
            // ИЗМЕНЕНИЕ: Логика создания папки теперь здесь.
            Files.createDirectories(getValidatedPath(path));
            return true;
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("[VFS] Failed to make dir {}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isDirectory(String path) {
        try {
            return Files.isDirectory(getValidatedPath(path));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public java.util.List<String> list(String path) throws IOException {
        try (Stream<Path> stream = Files.list(getValidatedPath(path))) {
            return stream.map(p -> p.getFileName().toString()).collect(Collectors.toList());
        }
    }

    @Override
    public boolean delete(String path) {
        try {
            return Files.deleteIfExists(getValidatedPath(path));
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("[VFS] Failed to delete {}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] readBytes(String path) throws IOException {
        Path target = getValidatedPath(path);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new IOException("File not found or is a directory: " + path);
        }
        return Files.readAllBytes(target);
    }

    @Override
    public boolean writeBytes(String path, byte[] data) {
        try {
            Path target = getValidatedPath(path);
            // Гарантируем, что родительская папка существует
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return true;
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("[VFS] Failed to write bytes to {}: {}", path, e.getMessage());
            return false;
        }
    }
}
