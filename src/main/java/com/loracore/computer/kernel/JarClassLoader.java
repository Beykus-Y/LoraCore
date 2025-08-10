// В файле: src/main/java/com/loracore/computer/kernel/JarClassLoader.java
package com.loracore.computer.kernel;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Загрузчик классов для ядра и приложений.
 * Использует стандартную логику URLClassLoader, но с делегированием "Parent-Last",
 * чтобы классы из JAR-файла имели приоритет над классами из мода (кроме общего API).
 */
public class JarClassLoader extends URLClassLoader {

    // API-пакеты, которые ВСЕГДА должны загружаться родительским загрузчиком,
    // чтобы избежать ClassCastException.
    private static final String[] API_PACKAGES = {
            "com.loracore.computer.kernel.", // IKernel, IKernelApi и т.д.
            "org.slf4j."                     // API логирования
    };

    // Новый конструктор, принимающий URL
    public JarClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Сначала проверяем, не загружен ли класс уже
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);

            // Проверяем, является ли класс частью общего API
            for (String apiPackage : API_PACKAGES) {
                if (name.startsWith(apiPackage)) {
                    // Если это API, делегируем загрузку родителю (Parent-First)
                    if (loadedClass == null) {
                        loadedClass = getParent().loadClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }

            // Если это не API, используем стратегию Parent-Last
            if (loadedClass == null) {
                try {
                    // 1. Сначала пытаемся найти класс в НАШИХ JAR-файлах
                    loadedClass = findClass(name);
                } catch (ClassNotFoundException e) {
                    // 2. Если не нашли, просим родителя
                    loadedClass = getParent().loadClass(name);
                }
            }

            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}