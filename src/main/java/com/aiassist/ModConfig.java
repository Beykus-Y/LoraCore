package com.aiassist;

public class ModConfig {
    // Статический экземпляр, который хранит текущую конфигурацию
    public static ModConfig instance = new ModConfig();

    // Поля конфигурации
    public String API_URL = "https://api.openai.com/v1";
    public String API_KEY = "";
    public String MODEL_ID = "gpt-4o";

    // Конструктор остается приватным, чтобы никто не мог создать объект напрямую
    private ModConfig() {}

    /**
     * Создает и возвращает новый экземпляр конфигурации со значениями по умолчанию.
     * Этот метод нужен для ConfigManager, чтобы восстанавливать конфиг при ошибках загрузки.
     * @return Новый экземпляр ModConfig.
     */
    public static ModConfig createDefault() {
        return new ModConfig();
    }
}