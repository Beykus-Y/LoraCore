package com.loracore.computer;

import com.google.gson.Gson;
import com.loracore.LoraCoreMod;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.luaj.vm2.LuaValue;

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
        // В ресурсах "папка" существует, если есть хоть один файл внутри нее.
        return !resourceManager.findResources(resourceRoot + path, p -> true).isEmpty();
    }

    @Override
    public LuaValue read(String path) {
        return resourceManager.getResource(toIdentifier(path)).map(resource -> {
            try (InputStream stream = resource.getInputStream()) {
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return LuaValue.valueOf(content);
            } catch (Exception e) {
                return LuaValue.NIL;
            }
        }).orElse(LuaValue.NIL);
    }

    @Override
    public String list(String path) {
        try {
            return GSON.toJson(resourceManager.findResources(resourceRoot + path, p -> true)
                    .keySet()
                    .stream()
                    .map(id -> id.getPath().substring(id.getPath().lastIndexOf('/') + 1))
                    .collect(Collectors.toList()));
        } catch(Exception e) {
            return null;
        }
    }

    // Эти методы не поддерживаются для read-only системы
    @Override
    public boolean write(String path, String content) { return false; }

    @Override
    public boolean makeDir(String path) { return false; }
}