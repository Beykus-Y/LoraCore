package com.loracore.computer;

/**
 * Конфигурации процессоров разных уровней (Silicon Lottery).
 * Определяет вольтаж, частоту и пределы стабильности.
 */
public class CpuTiers {

    // Класс-контейнер для настроек
    public static class Config {
        public final String name;
        public final double voltage;      // Вольтаж (влияет на нагрев)
        public final long targetFreq;     // Рабочая частота (Hz)
        public final double stabilityLimit; // Предел кремния (Silicon Lottery) в Hz

        public Config(String name, double voltage, long targetFreq, double stabilityLimit) {
            this.name = name;
            this.voltage = voltage;
            this.targetFreq = targetFreq;
            this.stabilityLimit = stabilityLimit;
        }
    }

    // --- ПРЕСЕТЫ ---

    // 1. Старый офисник (Медленно, холодно, надежно)
    public static final Config TIER_1_LOW_END = new Config(
            "Iron Potato",
            1.0,           // 1.0V
            300_000,       // 300 kHz
            550_000        // Лимит 550 kHz
    );

    // 2. Игровой ПК (Быстро, но греется)
    public static final Config TIER_2_MID_RANGE = new Config(
            "Gold Standard",
            1.35,          // 1.35V
            900_000,       // 900 kHz
            1_000_000      // Лимит 1 MHz
    );

    // 3. Флагман / Разгон (Очень быстро, нужна удача или охлаждение)
    public static final Config TIER_3_EXTREME = new Config(
            "Diamond Core",
            1.2,           // Хороший техпроцесс (низкий вольтаж)
            1_500_000,     // 1.5 MHz
            2_500_000      // Лимит 2.5 MHz (Золотой образец)
    );

    /**
     * Получить конфигурацию по имени архитектуры.
     */
    public static Config getConfigForArchitecture(String architecture) {
        return switch (architecture.toLowerCase()) {
            case "tier1", "low", "basic" -> TIER_1_LOW_END;
            case "tier2", "mid", "standard" -> TIER_2_MID_RANGE;
            case "tier3", "high", "extreme" -> TIER_3_EXTREME;
            default -> TIER_1_LOW_END; // По умолчанию самый слабый
        };
    }
}
