// Файл: ai-assistant/src/main/java/com/lora/tabletos/apps/FileExplorerApp.java
package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.ui.layout.GridLayout;
import com.lora.tabletos.ui.widgets.IconWidget;
import com.lora.tabletos.ui.widgets.ScrollPane;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;

import java.util.ArrayList;
import java.util.List;

public class FileExplorerApp implements IApplication {

    private IApplicationApi api;
    private ScrollPane scrollPane;
    private GridLayout gridLayout;
    private String currentPath = "/";
    private boolean isLoading = false;

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;

        // Настраиваем основной UI
        this.scrollPane = new ScrollPane(0, 20, 480, 220); // Оставляем 20px сверху для нав. панели
        this.gridLayout = new GridLayout(0, 0, 465, 6); // Ширина меньше для скроллбара
        gridLayout.setCellSize(70, 60);
        gridLayout.setSpacing(5, 10);

        this.scrollPane.setContent(gridLayout);

        // Загружаем содержимое корневой директории
        navigateTo(currentPath);
    }

    /**
     * Основной метод для загрузки и отображения содержимого директории.
     */
    private void navigateTo(String path) {
        this.currentPath = path;
        this.isLoading = true; // <-- Устанавливаем флаг загрузки

        // Создаем НОВЫЙ ПУСТОЙ грид и СРАЗУ же его устанавливаем.
        // Это очистит старые иконки.
        this.gridLayout = new GridLayout(0, 0, 465, 6);
        this.gridLayout.setCellSize(70, 60);
        this.gridLayout.setSpacing(5, 10);
        this.scrollPane.setContent(gridLayout);

        // Асинхронно запрашиваем список файлов
        api.getVfs().list(path).whenComplete((files, error) -> {
            // Вся логика ниже будет выполнена в фоновом потоке.

            // ✅ ИСПРАВЛЕНИЕ: Мы "прыгаем" обратно в поток отрисовки,
            // чтобы безопасно изменить UI.
            api.runOnRenderThread(() -> {
                this.isLoading = false; // <-- Сбрасываем флаг загрузки

                if (error != null) {
                    // TODO: Показать ошибку в UI
                    System.out.println("Error listing files: " + error.getMessage());
                    return;
                }
                if (files == null) return;

                List<String> mutableFiles = new ArrayList<>(files);

                // Теперь мы можем безопасно сортировать наш новый список
                mutableFiles.sort((f1, f2) -> {
                    // Используем более надежный способ, предполагая, что VFS API будет улучшен.
                    // Пока что оставляем вашу логику, но она должна быть заменена в будущем.
                    boolean isDir1 = !(f1.endsWith(".lua") || f1.endsWith(".jar"));
                    boolean isDir2 = !(f2.endsWith(".lua") || f2.endsWith(".jar"));
                    if (isDir1 && !isDir2) return -1;
                    if (!isDir1 && isDir2) return 1;
                    return f1.compareToIgnoreCase(f2);
                });

                if (!currentPath.equals("/")) {
                    IconWidget backButton = new IconWidget("..", true, () -> {
                        String parentPath = currentPath.substring(0, currentPath.lastIndexOf('/', currentPath.length() - 2) + 1);
                        navigateTo(parentPath);
                    });
                    gridLayout.addWidget(backButton);
                }

                for (String fileName : files) {
                    boolean isDir = !(fileName.endsWith(".lua") || fileName.endsWith(".jar"));
                    IconWidget icon = new IconWidget(fileName, isDir, () -> {
                        if (isDir) {
                            // Убедимся, что путь заканчивается слэшем
                            String correctedFileName = fileName.endsWith("/") ? fileName : fileName + "/";
                            navigateTo(currentPath + correctedFileName);
                        } else {
                            System.out.println("Attempting to run file: " + fileName);
                        }
                    });
                    gridLayout.addWidget(icon);
                }
            });
        });
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, 480, 240, 0x1E1E1E); // Фон

        // Рисуем текущий путь
        g.drawString("Path: " + currentPath, 10, 5, 0xFFFFFF);

        // Рендерим ScrollPane
        scrollPane.render(g, mouseX, mouseY);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        // Не даем обрабатывать клики, пока идет загрузка
        if (isLoading) {
            return false;
        }
        return scrollPane.onEvent(event);
    }

    @Override public void onResume() {}
    @Override public void onPause() {}
    @Override public void onClose() {}
}