// Файл: src/main/java/com/loracore/computer/ImageVfs.java
package com.loracore.computer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loracore.LoraCoreMod;
import org.luaj.vm2.LuaValue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ImageVfs implements IFileSystem {

    private final Path imagePath;
    private final Map<String, byte[]> fileContent = new ConcurrentHashMap<>();
    private final Map<String, Partition> partitions = new ConcurrentHashMap<>();
    private boolean isDirty = false;
    private static final Gson GSON = new Gson();

    private record Partition(String label, int size) {}

    public ImageVfs(Path worldSavePath, UUID fsUuid, int capacityKb) {
        this.imagePath = worldSavePath.resolve("loracore_vfs").resolve(fsUuid + ".img");
        loadOrCreateImage(capacityKb);
    }

    private void loadOrCreateImage(int capacityKb) {
        try {
            if (Files.exists(imagePath)) {
                byte[] data = Files.readAllBytes(imagePath);
                try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            fileContent.put(entry.getName(), zis.readAllBytes());
                        }
                    }
                }
                byte[] metaBytes = fileContent.get("meta.json");
                if (metaBytes != null) {
                    Type partitionMapType = new TypeToken<Map<String, Partition>>() {}.getType();
                    this.partitions.putAll(GSON.fromJson(new String(metaBytes, StandardCharsets.UTF_8), partitionMapType));
                }
            } else {
                partitions.put("boot", new Partition("boot", capacityKb > 0 ? capacityKb / 4 : 256));
                partitions.put("home", new Partition("home", capacityKb > 0 ? capacityKb * 3 / 4 : 768));
                isDirty = true;
                saveImage();
            }
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("Failed to load or create VFS image: {}", imagePath, e);
        }
    }

    private synchronized void saveImage() {
        if (!isDirty) return;
        try {
            fileContent.put("meta.json", GSON.toJson(partitions).getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (Map.Entry<String, byte[]> entry : fileContent.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }
            }
            Files.createDirectories(imagePath.getParent());
            Files.write(imagePath, baos.toByteArray());
            isDirty = false;
        } catch (IOException e) {
            LoraCoreMod.LOGGER.error("Failed to save VFS image: {}", imagePath, e);
        }
    }

    /**
     * Преобразует внешний путь (например, "/home/user.txt") во внутренний путь ZIP-файла ("home/user.txt").
     * Возвращает null, если путь некорректен или является корнем.
     */
    private String resolvePath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return null;
        }
        path = path.replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String internalPath = path.substring(1);
        String partition = internalPath.split("/")[0];

        if (partitions.containsKey(partition)) {
            return internalPath;
        }

        return null;
    }

    public String getMetadataAsJson() {
        Map<String, Object> metadata = new HashMap<>();
        long sizeBytes = 0;
        try {
            if (Files.exists(imagePath)) {
                sizeBytes = Files.size(imagePath);
            }
        } catch (IOException e) {
            // ignore
        }
        metadata.put("size", sizeBytes);
        metadata.put("partitions", this.partitions.keySet());
        return GSON.toJson(metadata);
    }

    @Override
    public boolean exists(String path) {
        // ✅ ИСПРАВЛЕНИЕ: Корень всегда существует.
        if (path.equals("/")) {
            return true;
        }
        String resolved = resolvePath(path);
        // Проверяем как явные файлы, так и неявные директории
        return resolved != null && (fileContent.containsKey(resolved) || isDirectory(path));
    }

    @Override
    public boolean isDirectory(String path) {
        // ✅ ИСПРАВЛЕНИЕ: Корень и названия разделов - это директории.
        if (path.equals("/")) {
            return true;
        }
        String potentialPartition = path.startsWith("/") ? path.substring(1) : path;
        if (partitions.containsKey(potentialPartition)) {
            return true;
        }

        String resolved = resolvePath(path);
        if (resolved == null) return false;

        String dirPath = resolved.endsWith("/") ? resolved : resolved + "/";
        // Директория существует, если есть хотя бы один файл с таким префиксом.
        return fileContent.keySet().stream().anyMatch(k -> k.startsWith(dirPath));
    }

    @Override
    public String list(String path) {
        // ✅ ИСПРАВЛЕНИЕ: Если запрашиваем корень, возвращаем список разделов.
        if (path.equals("/")) {
            return GSON.toJson(new ArrayList<>(partitions.keySet()));
        }

        String resolvedPrefix = resolvePath(path);
        if (resolvedPrefix == null) {
            // Если путь - это имя раздела (например, /home), тоже обрабатываем его
            String potentialPartition = path.startsWith("/") ? path.substring(1) : path;
            if(partitions.containsKey(potentialPartition)) {
                resolvedPrefix = potentialPartition;
            } else {
                return "[]"; // Неверный путь
            }
        }

        final String prefix = resolvedPrefix.endsWith("/") ? resolvedPrefix : resolvedPrefix + "/";

        List<String> entries = fileContent.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()).split("/")[0]) // Получаем только следующую часть пути
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        return GSON.toJson(entries);
    }

    // ----- Остальные методы (read, write, etc.) остаются без изменений -----

    @Override
    public LuaValue read(String path) {
        try {
            byte[] bytes = readBytes(path);
            return bytes != null ? LuaValue.valueOf(new String(bytes, StandardCharsets.UTF_8)) : LuaValue.NIL;
        } catch (IOException e) {
            return LuaValue.NIL;
        }
    }

    @Override
    public boolean write(String path, String content) {
        return writeBytes(path, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean makeDir(String path) {
        // В ZIP-архиве директории создаются неявно при записи файла.
        // Для эмуляции можно создать "пустой" файл-маркер, если потребуется.
        // Пока что просто возвращаем true, так как команда cd будет работать.
        return true;
    }

    @Override
    public boolean delete(String path) {
        String resolved = resolvePath(path);
        if (resolved == null) return false;
        boolean removed = fileContent.remove(resolved) != null;
        if (removed) {
            isDirty = true;
            saveImage();
        }
        return removed;
    }

    @Override
    public byte[] readBytes(String path) throws IOException {
        String resolved = resolvePath(path);
        if (resolved == null) throw new IOException("Invalid path or partition: " + path);
        byte[] data = fileContent.get(resolved);
        if (data == null) throw new IOException("File not found: " + path);
        return data;
    }

    @Override
    public boolean writeBytes(String path, byte[] data) {
        String resolved = resolvePath(path);
        if (resolved == null) return false;
        fileContent.put(resolved, data);
        isDirty = true;
        saveImage();
        return true;
    }
}