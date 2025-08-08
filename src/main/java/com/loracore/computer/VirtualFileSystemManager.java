package com.loracore.computer;

import com.loracore.LoraCoreMod;
// ИСПРАВЛЕНО: Добавлены недостающие импорты
import com.loracore.network.vfs.VfsRequestC2SPacket;
import com.loracore.network.vfs.VfsResponseS2CPacket;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.luaj.vm2.LuaValue;
import java.util.Base64;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.nio.file.Files;

public class VirtualFileSystemManager {
    private static VirtualFileSystemManager INSTANCE;
    private ResourceManager resourceManager;
    private Path worldSavePath;
    
    // ИСПРАВЛЕНО: Уменьшаем лимит до безопасного значения для Minecraft
    private static final int MAX_CHUNK_SIZE = 30000; // ~22KB в base64 (безопасно для Minecraft)
    private static final int CHUNK_SIZE = 25000; // Размер одного чанка (безопасно для Minecraft)

    // Приватный конструктор для синглтона
    private VirtualFileSystemManager() {}

    public static synchronized VirtualFileSystemManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new VirtualFileSystemManager();
        }
        return INSTANCE;
    }

    public void initialize(MinecraftServer server) {
        this.worldSavePath = server.getSavePath(WorldSavePath.ROOT);
        LoraCoreMod.LOGGER.info("VirtualFileSystemManager initialized. World path: {}", worldSavePath);
    }

    public static void registerResourceManagerListener() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(LoraCoreMod.MOD_ID, "vfs_resource_listener");
            }

            @Override
            public void reload(ResourceManager manager) {
                getInstance().resourceManager = manager;
                LoraCoreMod.LOGGER.info("VFS ResourceManager has been reloaded.");
            }
        });
    }

    public VFSResponse performOperation(UUID fsUuid, VfsRequestC2SPacket.Operation op, String path, String content) {
        // ИСПРАВЛЕНИЕ: Добавляем проверку на null для fsUuid
        if (fsUuid == null) {
            LoraCoreMod.LOGGER.error("VFS Manager: fsUuid is null!");
            return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");
        }
        
        if (worldSavePath == null || resourceManager == null) {
            LoraCoreMod.LOGGER.error("VFS Manager not fully initialized! Cannot perform operations.");
            return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");
        }

        IFileSystem hdd = new WorldStorageVFS(worldSavePath, fsUuid);
        IFileSystem rom = new ResourceVFS(resourceManager, "os");

        // ИСПРАВЛЕНО: Синтаксис switch для enum
        switch (op) {
            case EXISTS:
                boolean exists = hdd.exists(path) || rom.exists(path);
                return new VFSResponse(exists ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE, "");

            case READ:
                LuaValue hddContent = hdd.read(path);
                if (!hddContent.isnil()) {
                    return new VFSResponse(VfsResponseS2CPacket.ResponseType.STRING, hddContent.tojstring());
                }
                LuaValue romContent = rom.read(path);
                if (!romContent.isnil()) {
                    return new VFSResponse(VfsResponseS2CPacket.ResponseType.STRING, romContent.tojstring());
                }
                return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");

            case READ_BYTES:
                try {
                    Path filePath = new WorldStorageVFS(worldSavePath, fsUuid).getValidatedPath(path);
                    if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                        byte[] bytes = Files.readAllBytes(filePath);
                        String base64 = Base64.getEncoder().encodeToString(bytes);
                        
                        LoraCoreMod.LOGGER.info("File {}: {} bytes, {} base64 chars, limit: {}", 
                            path, bytes.length, base64.length(), MAX_CHUNK_SIZE);
                        
                        // ИСПРАВЛЕНО: Возвращаем весь файл как LARGE_DATA, если он большой
                        if (base64.length() > MAX_CHUNK_SIZE) {
                            LoraCoreMod.LOGGER.info("File {} is large ({} base64 chars), will be sent as LARGE_DATA", path, base64.length());
                            // Возвращаем ВЕСЬ файл, а не только первый чанк
                            return new VFSResponse(VfsResponseS2CPacket.ResponseType.LARGE_DATA, base64);
                        }
                        
                        LoraCoreMod.LOGGER.info("File {} is small enough ({} base64 chars), sending as STRING", path, base64.length());
                        return new VFSResponse(VfsResponseS2CPacket.ResponseType.STRING, base64);
                    }
                } catch (IOException e) {
                    LoraCoreMod.LOGGER.error("Failed to read bytes for VFS: {}", e.getMessage());
                }
                return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");

            case ISDIR:
                boolean isDir = hdd.isDirectory(path) || rom.isDirectory(path);
                return new VFSResponse(isDir ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE, "");

            case WRITE:
                boolean wrote = hdd.write(path, content);
                return new VFSResponse(wrote ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE, "");

            case MAKEDIR:
                boolean madeDir = hdd.makeDir(path);
                return new VFSResponse(madeDir ? VfsResponseS2CPacket.ResponseType.TRUE : VfsResponseS2CPacket.ResponseType.FALSE, "");

            case LIST:
                String listJson = hdd.list(path);
                if (listJson != null) {
                    return new VFSResponse(VfsResponseS2CPacket.ResponseType.TABLE_JSON, listJson);
                }
                // Добавим поиск и в ROM, если на диске папка пуста или ее нет
                String romListJson = rom.list(path);
                if (romListJson != null) {
                    return new VFSResponse(VfsResponseS2CPacket.ResponseType.TABLE_JSON, romListJson);
                }
                return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");

            default:
                return new VFSResponse(VfsResponseS2CPacket.ResponseType.NIL, "");
        }
    }

    public record VFSResponse(VfsResponseS2CPacket.ResponseType type, String data) {}
}