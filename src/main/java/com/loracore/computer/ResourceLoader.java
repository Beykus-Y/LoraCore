// Файл: src/main/java/com/loracore/computer/ResourceLoader.java
package com.loracore.computer;

/**
 * Функциональный интерфейс для абстрагирования загрузки ресурсов.
 * Это позволяет VirtualMachine запрашивать файлы, не зная, как они загружаются.
 */
@FunctionalInterface
public interface ResourceLoader {
    /**
     * Загружает ресурс по указанному пути.
     * @param path Путь к ресурсу (например, "os/bios.lua").
     * @return Содержимое файла в виде строки или null, если не найден.
     */
    String load(String path);
}