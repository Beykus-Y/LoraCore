// Полный исправленный файл: src/client/java/com/loracore/computer/jkernel/JavaRuntime.java
package com.loracore.computer.jkernel;

import com.loracore.computer.ClientVFS;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.KernelManager;
import com.loracore.computer.kernel.KernelEvent;
import com.loracore.gui.TabletScreen;

public class JavaRuntime implements IRuntimeEnvironment {

    private final KernelManager kernelManager;
    private final boolean isOwner;
    // Поле parentScreen больше не нужно, удаляем его
    // private final TabletScreen parentScreen;

    public JavaRuntime(TabletScreen parentScreen, ClientVFS vfs, boolean isOwner) {
        // this.parentScreen = parentScreen; // Удаляем присваивание
        this.isOwner = isOwner;
        this.kernelManager = new KernelManager(vfs, parentScreen.getTabletUuid(), parentScreen, parentScreen.getScreenImage(), isOwner);

        // Эта строка остается, она важна для моста Java -> Lua
        this.kernelManager.setLuaExecutor(parentScreen.getLuaExecutor()::execute);
    }

    @Override
    public void boot(String bootPath) {
        kernelManager.boot(bootPath);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        // Вся логика рендеринга инкапсулирована в KernelManager.
        // Он сам решает, что рисовать (экран загрузки, BSOD или ничего).
        kernelManager.render(mouseX, mouseY, delta);
    }

    // --- Новый, исправленный метод ---
    @Override
    public boolean needsClientSideRendering() {
        // Возвращаем true только если это владелец планшета
        return isOwner;
    }

    // --- Остальные методы остаются без изменений ---

    @Override
    public void tick() {
        kernelManager.tick();
    }

    @Override
    public void shutdown() {
        kernelManager.shutdown();
    }

    @Override
    public boolean isRunning() {
        return kernelManager.isRunning();
    }

    @Override
    public String getCrashMessage() {
        return kernelManager.getCrashMessage();
    }

    // ... (все методы onKeyPressed, onMouseClicked и т.д. остаются как есть)
    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        kernelManager.onEvent(new KernelEvent.KeyPressed(keyCode, scanCode, modifiers));
        return true;
    }

    @Override
    public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        kernelManager.onEvent(new KernelEvent.KeyReleased(keyCode, scanCode, modifiers));
        return true;
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        kernelManager.onEvent(new KernelEvent.CharTyped(chr, modifiers));
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        kernelManager.onEvent(new KernelEvent.MouseScrolled(mouseX, mouseY, hAmount, vAmount));
        return false;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        kernelManager.onEvent(new KernelEvent.MouseClicked(mouseX, mouseY, button));
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        kernelManager.onEvent(new KernelEvent.MouseReleased(mouseX, mouseY, button));
        return false;
    }
}