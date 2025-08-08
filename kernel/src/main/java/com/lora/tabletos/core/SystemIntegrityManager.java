package com.lora.tabletos.core;

import com.loracore.computer.kernel.IKernelVfs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Проверяет наличие обязательных директорий и создает их при отсутствии.
 */
public class SystemIntegrityManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemIntegrityManager.class);
    
    private final IKernelVfs vfs;
    private final Consumer<String> statusCallback;
    private final List<String> requiredDirectories = new ArrayList<>();
    
    public SystemIntegrityManager(IKernelVfs vfs, Consumer<String> statusCallback) {
        this.vfs = vfs;
        this.statusCallback = statusCallback;
        initializeRequiredDirectories();
    }
    
    /**
     * Инициализирует список обязательных директорий.
     */
    private void initializeRequiredDirectories() {
        requiredDirectories.add("/home");
        requiredDirectories.add("/etc");
        requiredDirectories.add("/user");
        requiredDirectories.add("/home/user/apps");
    }
    
    /**
     * Запускает проверку целостности системы.
     * 
     * @return CompletableFuture, который завершится с true при успехе или false при сбое
     */
    public CompletableFuture<Boolean> run() {
        LOGGER.info("Starting system integrity checks...");
        statusCallback.accept("Initializing system checks...");
        
        CompletableFuture<Boolean> allChecks = CompletableFuture.completedFuture(true);
        
        for (String directory : requiredDirectories) {
            allChecks = allChecks.thenCompose(previousResult -> {
                if (!previousResult) {
                    return CompletableFuture.completedFuture(false);
                }
                return checkDirectory(directory);
            });
        }
        
        return allChecks.thenApply(success -> {
            if (success) {
                LOGGER.info("All system integrity checks passed successfully.");
                statusCallback.accept("System OK");
            } else {
                LOGGER.error("One or more system integrity checks failed.");
                statusCallback.accept("System integrity failed!");
            }
            return success;
        });
    }
    
    /**
     * Проверяет существование директории и создает её при необходимости.
     */
    private CompletableFuture<Boolean> checkDirectory(String path) {
        statusCallback.accept("Checking " + path + "...");
        
        return vfs.exists(path).thenCompose(exists -> {
            if (exists) {
                LOGGER.debug("Directory '{}' exists", path);
                return CompletableFuture.completedFuture(true);
            } else {
                statusCallback.accept("Repairing " + path + "...");
                LOGGER.info("Directory '{}' does not exist, creating...", path);
                
                return vfs.makeDir(path).thenApply(success -> {
                    if (success) {
                        LOGGER.info("Successfully created directory '{}'", path);
                        return true;
                    } else {
                        LOGGER.error("Failed to create directory '{}'", path);
                        return false;
                    }
                });
            }
        }).exceptionally(throwable -> {
            LOGGER.error("Error checking/creating directory '{}'", path, throwable);
            return false;
        });
    }
}
