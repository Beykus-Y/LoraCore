// Полный исправленный файл: src/client/java/com/loracore/computer/jkernel/JavaRuntime.java
package com.loracore.computer.jkernel;

import com.loracore.computer.ClientVFS;
import com.loracore.computer.IRuntimeEnvironment;
import com.loracore.computer.KernelManager;
import com.loracore.computer.kernel.KernelEvent;
import com.loracore.gui.TabletScreen;
import net.minecraft.client.gui.DrawContext;

public class JavaRuntime implements IRuntimeEnvironment {

    private final KernelManager kernelManager;

    public JavaRuntime(TabletScreen parentScreen, ClientVFS vfs) {
        this.kernelManager = new KernelManager(vfs, parentScreen);
    }

    @Override
    public void boot(String bootPath) {
        kernelManager.boot(bootPath);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        kernelManager.render(context, mouseX, mouseY, delta);
    }

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
        return true;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        kernelManager.onEvent(new KernelEvent.MouseClicked(mouseX, mouseY, button));
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        kernelManager.onEvent(new KernelEvent.MouseReleased(mouseX, mouseY, button));
        return true;
    }
}