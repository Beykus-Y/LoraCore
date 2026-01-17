package lora.emulator.gui;

import lora.emulator.bus.GpuDevice;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class DebugWindow extends JFrame {
    private final GpuDevice gpu;
    private final BufferedImage buffer;
    private final int[] bufferData;
    private final int scale;

    public DebugWindow(GpuDevice gpu, int scale) {
        this.gpu = gpu;
        this.scale = scale;

        setTitle("Lora System Monitor | GPU: " + gpu.getWidth() + "x" + gpu.getHeight());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // Размер окна + рамки
        int w = gpu.getWidth() * scale;
        int h = gpu.getHeight() * scale;

        // Canvas — это компонент, на котором мы будем рисовать аппаратно
        Canvas canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(w, h));
        canvas.setFocusable(false); // Чтобы клавиатуру ловил JFrame
        add(canvas);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // Создаем буфер изображения (Бэк-буфер)
        buffer = new BufferedImage(gpu.getWidth(), gpu.getHeight(), BufferedImage.TYPE_INT_RGB);
        bufferData = ((DataBufferInt) buffer.getRaster().getDataBuffer()).getData();

        // Создаем стратегию буферизации (Двойная буферизация)
        canvas.createBufferStrategy(2);
        BufferStrategy bs = canvas.getBufferStrategy();

        // Запускаем рендер-луп в отдельном потоке
        new Thread(() -> {
            while (true) {
                render(bs);
                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Render-Thread").start();
    }

    private void render(BufferStrategy bs) {
        // 1. Копируем VRAM в буфер (Атомарно насколько возможно)
        // Синхронизация здесь убила бы производительность, поэтому надеемся на лучшее.
        // Артефакты (tearing) допустимы в эмуляторах.
        System.arraycopy(gpu.vram, 0, bufferData, 0, Math.min(gpu.vram.length, bufferData.length));

        // 2. Рисуем
        do {
            do {
                Graphics2D g = (Graphics2D) bs.getDrawGraphics();

                // Очистка (не обязательна, если мы рисуем поверх всего)
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());

                // Рисуем отмасштабированный экран
                // Nearest Neighbor для четких пикселей
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(buffer, 0, 0, buffer.getWidth() * scale, buffer.getHeight() * scale, null);

                g.dispose();
            } while (bs.contentsRestored());
            bs.show();
        } while (bs.contentsLost());
    }
}