package com.loracore.computer;

import com.loracore.LoraCoreClient;
import com.loracore.computer.lualibs.LuaRuntime;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;

/**
 * Централизованный исполнитель Lua-скриптов.
 * Единственная точка для запуска Lua-скриптов в системе.
 */
public class LuaExecutor {
    
    private final TabletScreen parentScreen;
    private final ClientVFS vfs;
    
    public LuaExecutor(TabletScreen parentScreen, ClientVFS vfs) {
        this.parentScreen = parentScreen;
        this.vfs = vfs;
    }
    
    /**
     * Выполняет Lua-скрипт, создавая новый экземпляр LuaRuntime
     * @param scriptContent Содержимое Lua-скрипта для выполнения
     */
    public void execute(String scriptContent) {
        if (MinecraftClient.getInstance() != null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    // Создаем новый LuaRuntime для каждого выполнения
                    // LuaRuntime автоматически получит правильный размер RAM из TabletScreen
                    LuaRuntime luaRuntime = new LuaRuntime(parentScreen, vfs);
                    
                    // Сохраняем в TabletScreen для управления жизненным циклом
                    parentScreen.setActiveShell(luaRuntime);
                    
                    // Выполняем скрипт
                    luaRuntime.executeScript(scriptContent);
                    
                    LoraCoreClient.LOGGER.info("Lua script executed successfully via LuaExecutor");
                } catch (Exception e) {
                    LoraCoreClient.LOGGER.error("Failed to execute Lua script via LuaExecutor: {}", e.getMessage());
                    parentScreen.setCrashState("Lua execution failed: " + e.getMessage());
                }
            });
        }
    }
}
