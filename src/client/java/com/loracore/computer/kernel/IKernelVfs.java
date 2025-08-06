// Полный файл: src/client/java/com/loracore/computer/kernel/IKernelVfs.java
package com.loracore.computer.kernel;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Безопасный, асинхронный интерфейс для взаимодействия с виртуальной файловой системой (VFS) планшета.
 * Все операции возвращают CompletableFuture, заставляя ядро работать в неблокирующем стиле.
 */
public interface IKernelVfs {

    /**
     * Проверяет, существует ли файл или директория по указанному пути.
     * @param path Абсолютный путь внутри VFS (например, "/home/user/test.txt").
     * @return Future, который завершится со значением true, если путь существует.
     */
    CompletableFuture<Boolean> exists(String path);

    /**
     * Проверяет, является ли путь директорией.
     * @param path Абсолютный путь внутри VFS.
     * @return Future, который завершится со значением true, если путь является директорией.
     */
    CompletableFuture<Boolean> isDirectory(String path);

    /**
     * Асинхронно читает все байты из файла.
     * @param path Абсолютный путь к файлу.
     * @return Future, который завершится с Optional, содержащим массив байт.
     *         Optional будет пустым, если файл не найден или является директорией.
     */
    CompletableFuture<Optional<byte[]>> readBytes(String path);

    /**
     * Асинхронно записывает массив байт в файл. Если файл существует, он будет перезаписан.
     * Если родительских директорий не существует, они будут созданы.
     * @param path Абсолютный путь к файлу.
     * @param data Массив байт для записи.
     * @return Future, который завершится со значением true в случае успеха.
     */
    CompletableFuture<Boolean> writeBytes(String path, byte[] data);

    /**
     * Создает директорию, включая все несуществующие родительские директории.
     * @param path Абсолютный путь к директории, которую нужно создать.
     * @return Future, который завершится со значением true в случае успеха.
     */
    CompletableFuture<Boolean> makeDir(String path);

    /**
     * Удаляет файл или директорию.
     * Директория должна быть пуста для успешного удаления.
     * @param path Абсолютный путь.
     * @return Future, который завершится со значением true в случае успеха.
     */
    CompletableFuture<Boolean> delete(String path);

    /**
     * Получает список имен файлов и директорий внутри указанной директории.
     * @param path Абсолютный путь к директории.
     * @return Future, который завершится со списком имен.
     *         В случае ошибки или если путь не является директорией, Future завершится с исключением.
     */
    CompletableFuture<List<String>> list(String path);
}