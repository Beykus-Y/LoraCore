// Полный исправленный файл: src/client/java/com/loracore/computer/lualibs/LuaRuntime.java
package com.loracore.computer.lualibs;

import com.loracore.computer.*;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import com.loracore.LoraCoreClient;

public class LuaRuntime implements IRuntimeEnvironment {

    private final VirtualMachine vm;
    private final TerminalRenderer terminalRenderer;
    private final ClientVFS vfs;
    private final TabletScreen parentScreen;

    public LuaRuntime(TabletScreen parentScreen, ClientVFS vfs) {
        this.parentScreen = parentScreen;
        this.vfs = vfs;
        // Lua в текстовом режиме все еще использует TerminalRenderer как реализацию интерфейса Terminal
        this.terminalRenderer = new TerminalRenderer(parentScreen);
        UUID tabletUuid = parentScreen.getTabletUuid();

        ResourceLoader loader = (path) -> {
            try {
                Identifier id = path.contains(":") ? new Identifier(path) : new Identifier("loracore", path);
                Optional<Resource> resourceOpt = MinecraftClient.getInstance().getResourceManager().getResource(id);
                if (resourceOpt.isPresent()) {
                    try (InputStream stream = resourceOpt.get().getInputStream()) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                // Игнорировать
            }
            return null;
        };

        // Создаем VM с правильными аргументами, включая новый обработчик перезагрузки
        // ИСПРАВЛЕНИЕ: Получаем размер RAM динамически из компонентов планшета
        int ramSizeKb = getTabletRamSize(parentScreen);
        
        this.vm = new VirtualMachine(
                "lora_v1_lua",
                ramSizeKb, // RAM - теперь динамический размер
                terminalRenderer, // TerminalRenderer реализует интерфейс Terminal
                loader,
                vfs, // Блокирующая обертка над асинхронным VFS
                vfs.getFsUuid(),
                tabletUuid, // <-- UUID самого планшета
                parentScreen::rebootIntoJava // <-- Передаем ссылку на метод
        );
    }

    @Override
    public void boot(String bootPath) {
        String bootScriptContent;
        // Lua-режим для простоты всегда грузит скрипт восстановления из ресурсов
        if (vm.getResourceLoader() != null) {
            bootScriptContent = vm.getResourceLoader().load(bootPath);
            vm.start(bootScriptContent);
        } else {
            vm.setCrashState("ResourceLoader not available.");
        }
    }

    /**
     * Загружает и выполняет Lua-скрипт из строки
     */
    public void bootFromString(String scriptContent) {
        if (scriptContent != null && !scriptContent.trim().isEmpty()) {
            vm.start(scriptContent);
        } else {
            vm.setCrashState("Empty script content provided.");
        }
    }

    /**
     * Выполняет Lua-скрипт в уже настроенной виртуальной машине
     * @param scriptContent Содержимое Lua-скрипта для выполнения
     */
    public void executeScript(String scriptContent) {
        if (scriptContent != null && !scriptContent.trim().isEmpty()) {
            vm.start(scriptContent);
        } else {
            vm.setCrashState("Empty script content provided.");
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        // Вся логика рендера теперь на сервере. Этот метод пуст.
        // Lua-скрипты отправляют команды через GPU API.
    }

    @Override
    public void tick() {
        if (vm.getCrashMessage() != null && parentScreen != null) {
            // Передаем управление ошибкой напрямую в TabletScreen
            if (parentScreen instanceof TabletScreen) {
                ((TabletScreen) parentScreen).setLuaCrashState(vm.getCrashMessage());
            }
            // Важно! Выключаем VM, чтобы она не продолжала работать в фоне
            vm.shutdown();
        }
    }

    @Override
    public void shutdown() {
        if (this.vm != null) {
            this.vm.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return vm.isRunning();
    }

    @Override
    public String getCrashMessage() {
        return vm.getCrashMessage();
    }

    // --- Обработка ввода для текстового режима ---
    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        // ДИАГНОСТИКА: Логируем все нажатия клавиш
        LoraCoreClient.LOGGER.info("LuaRuntime.onKeyPressed: keyCode={}, scanCode={}, modifiers={}", keyCode, scanCode, modifiers);
        
        if (modifiers == GLFW.GLFW_MOD_CONTROL && keyCode == GLFW.GLFW_KEY_C) {
            LoraCoreClient.LOGGER.info("LuaRuntime: Sending interrupt signal");
            vm.pushEvent("signal", "interrupt");
            return true;
        }
        
        LoraCoreClient.LOGGER.info("LuaRuntime: Pushing key event: key={}", keyCode);
        vm.pushEvent("key", keyCode);
        return true;
    }

    @Override
    public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        terminalRenderer.onCharTyped(chr);
        vm.pushEvent("char", String.valueOf(chr));
        return true;
    }

    // В текстовом режиме мышь не используется
    @Override public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) { return false; }
    @Override public boolean onMouseClicked(double mouseX, double mouseY, int button) { return false; }
    @Override public boolean onMouseReleased(double mouseX, double mouseY, int button) { return false; }

    @Override
    public boolean needsClientSideRendering() {
        return false; // Lua всегда рендерится на сервере
    }
    /**
     * Возвращает виртуальную машину для доступа к pushEvent
     */
    public VirtualMachine getVm() {
        return this.vm;
    }
    
    /**
     * Получает размер RAM планшета из его компонентов
     * По умолчанию возвращает 512 KB (RAM T1)
     */
    private int getTabletRamSize(TabletScreen tabletScreen) {
        try {
            // ИСПРАВЛЕНИЕ: Получаем размер RAM напрямую из TabletScreen
            int ramSize = tabletScreen.getTabletRamKb();
            LoraCoreClient.LOGGER.info("LuaRuntime: Получен размер RAM из TabletScreen: {} KB", ramSize);
            
            if (ramSize > 0) {
                return ramSize;
            }
            
            // Fallback: если размер не установлен, возвращаем стандартное значение
            LoraCoreClient.LOGGER.warn("LuaRuntime: Размер RAM не установлен, используем значение по умолчанию: 512 KB");
            return 512; // 512 KB - стандартный размер для RAM T1
        } catch (Exception e) {
            // В случае ошибки возвращаем безопасное значение по умолчанию
            LoraCoreClient.LOGGER.error("LuaRuntime: Ошибка при получении размера RAM: {}", e.getMessage());
            return 512;
        }
    }
}