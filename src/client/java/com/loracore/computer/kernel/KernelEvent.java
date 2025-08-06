// Полный исправленный файл: src/client/java/com/loracore/computer/kernel/KernelEvent.java
package com.loracore.computer.kernel;

/**
 * Запечатанный класс, представляющий все возможные события,
 * которые могут быть отправлены в ядро (IKernel).
 * Использование 'sealed' и 'permits' гарантирует, что мы не сможем создать
 * неизвестный тип события, что повышает безопасность и надежность API.
 */
public abstract sealed class KernelEvent permits
        KernelEvent.KeyPressed,
        KernelEvent.KeyReleased,
        KernelEvent.CharTyped,
        KernelEvent.MouseScrolled,
        KernelEvent.MouseClicked,
        KernelEvent.MouseReleased {

    // Приватный конструктор, чтобы нельзя было унаследоваться извне этого файла
    private KernelEvent() {}

    /**
     * Событие нажатия клавиши на клавиатуре.
     */
    public static final class KeyPressed extends KernelEvent {
        public final int keyCode;
        public final int scanCode;
        public final int modifiers;

        public KeyPressed(int keyCode, int scanCode, int modifiers) {
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }

        // Геттеры для удобства
        public int keyCode() { return keyCode; }
        public int scanCode() { return scanCode; }
        public int modifiers() { return modifiers; }

        // IDE может сгенерировать equals, hashCode, toString
    }

    /**
     * Событие отпускания клавиши на клавиатуре.
     */
    public static final class KeyReleased extends KernelEvent {
        public final int keyCode;
        public final int scanCode;
        public final int modifiers;

        public KeyReleased(int keyCode, int scanCode, int modifiers) {
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
        }
    }

    /**
     * Событие ввода символа.
     */
    public static final class CharTyped extends KernelEvent {
        public final char chr;
        public final int modifiers;

        public CharTyped(char chr, int modifiers) {
            this.chr = chr;
            this.modifiers = modifiers;
        }
    }

    /**
     * Событие прокрутки колеса мыши.
     */
    public static final class MouseScrolled extends KernelEvent {
        public final double mouseX;
        public final double mouseY;
        public final double horizontalAmount;
        public final double verticalAmount;

        public MouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.horizontalAmount = horizontalAmount;
            this.verticalAmount = verticalAmount;
        }
    }

    /**
     * Событие нажатия кнопки мыши.
     */
    public static final class MouseClicked extends KernelEvent {
        public final double mouseX;
        public final double mouseY;
        public final int button;

        public MouseClicked(double mouseX, double mouseY, int button) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.button = button;
        }
    }

    /**
     * Событие отпускания кнопки мыши.
     */
    public static final class MouseReleased extends KernelEvent {
        public final double mouseX;
        public final double mouseY;
        public final int button;

        public MouseReleased(double mouseX, double mouseY, int button) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.button = button;
        }
    }
}