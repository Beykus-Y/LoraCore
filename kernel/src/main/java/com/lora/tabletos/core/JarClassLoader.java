package com.lora.tabletos.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Загружает классы из байт-массива (нашего JAR-файла из VFS).
 * Не имеет доступа к файловой системе хоста.
 */
public class JarClassLoader extends URLClassLoader {
    // Кэш для уже загруженных классов, чтобы не определять их дважды.
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();

    // Мы используем пустой массив URL, чтобы он не пытался искать файлы на диске.
    public JarClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    /**
     * Главный метод, который мы будем вызывать для загрузки классов из нашего JAR.
     * @param name Имя класса (например, "com.myos.KernelMain")
     * @param data Байт-код этого класса.
     * @return Загруженный класс.
     */
    public Class<?> defineClassFromData(String name, byte[] data) {
        return classCache.computeIfAbsent(name, n -> defineClass(n, data, 0, data.length));
    }
}
