package lora.emulator.bus;

public class GpuSpecs {

    public static class Config {
        public final String name;
        public final int width;
        public final int height;
        public final boolean hasAcceleration; // Tier 2+
        public final boolean hasBlitter;      // Tier 3+
        public final int vramSize;
        public final int pixelsPerTick;       // Скорость GPU (пикселей за такт шины)

        public Config(String name, int width, int height, boolean accel, boolean blit, int speed) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.hasAcceleration = accel;
            this.hasBlitter = blit;
            this.pixelsPerTick = speed;
            this.vramSize = width * height; // + регистры сверху
        }
    }

    // --- ПРЕСЕТЫ ---

    // 1. Текстовый терминал / CGA. 128x128. Рисуй пиксели сам, страдалец.
    public static final Config TIER_1_BASIC = new Config(
            "CGA-128", 128, 128, false, false, 0
    );

    // 2. Ускоритель. 256x256. Умеет заливать области.
    public static final Config TIER_2_ACCEL = new Config(
            "VGA-256", 256, 256, true, false, 32 // Рисует 32 пикселя за такт
    );

    // 3. Супер-карта. 512x384. Умеет копировать куски памяти (спрайты/окна).
    public static final Config TIER_3_BLASTER = new Config(
            "SVGA-512", 512, 384, true, true, 128 // Монстр, 128 пикселей за такт
    );
}