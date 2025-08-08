# Архитектура LoraCore OS

## Проблемы и решения

### 1. Проблема с размером пакетов

**Проблема**: JAR-файлы слишком большие (45976 символов) для передачи через сетевые пакеты (максимум 32767).

**Решение**: 
- Добавлен новый тип ответа `LARGE_DATA` в `VfsResponseS2CPacket`
- Большие файлы разделяются на части по 30000 символов
- Клиент собирает части обратно

### 2. Архитектура приложений

**Проблема**: AI Assistant был встроен в ядро, а не являлся отдельным приложением.

**Решение**:
- Создан отдельный проект `ai-assistant/` для AI Assistant
- Приложения теперь загружаются как отдельные JAR-файлы
- Ядро содержит только интерфейсы и базовые компоненты

## Структура проектов

### Основной проект (loracore)
```
src/
├── main/java/com/loracore/
│   ├── computer/           # Виртуальная машина
│   ├── network/           # Сетевая коммуникация
│   └── ...
└── client/java/com/loracore/
    └── computer/          # Клиентская часть
```

### Ядро (kernel)
```
kernel/
├── src/main/java/com/lora/tabletos/
│   ├── core/              # Основные компоненты ядра
│   │   ├── IApplication.java
│   │   ├── IApplicationApi.java
│   │   └── LoraOSKernel.java
│   ├── ui/                # Пользовательский интерфейс
│   └── state/             # Управление состоянием
```

### AI Assistant (ai-assistant)
```
ai-assistant/
├── src/main/java/com/lora/tabletos/apps/
│   └── AiAssistantApp.java
└── build.gradle
```

## Сборка и установка

### 1. Сборка ядра
```bash
cd kernel
./gradlew build
# Результат: build/libs/kernel-1.3.0.jar
```

### 2. Сборка AI Assistant
```bash
cd ai-assistant
./gradlew build
# Результат: build/libs/ai_assistant-1.0.0.jar
```

### 3. Установка
1. Скопируйте `kernel-1.3.0.jar` в `/boot/kernel.jar`
2. Скопируйте `ai_assistant-1.0.0.jar` в `/home/user/apps/`

## API для приложений

### IApplication
```java
public interface IApplication {
    void onLoad(IApplicationApi api);
    void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta);
    void onEvent(KernelEvent event);
    void onClose();
}
```

### IApplicationApi
```java
public interface IApplicationApi {
    IKernelVfs getVfs();
    IKernelGraphics getGraphics();
    CompletableFuture<String> askAI(String prompt);
    int[] getScreenSize();
    CompletableFuture<Boolean> runLuaScript(String path);
}
```

## Создание приложений

### 1. Создайте класс приложения
```java
public class MyApp implements IApplication {
    @Override
    public void onLoad(IApplicationApi api) {
        // Инициализация
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        // Отрисовка
    }
    
    @Override
    public void onEvent(KernelEvent event) {
        // Обработка событий
    }
    
    @Override
    public void onClose() {
        // Очистка
    }
}
```

### 2. Создайте манифест
```
Manifest-Version: 1.0
App-Main-Class: com.example.MyApp
```

### 3. Упакуйте в JAR
```bash
jar -cf myapp.jar -C bin . META-INF/
```

## Следующие шаги

1. **Исправить проблему с размером пакетов**:
   - Реализовать разделение больших файлов на части
   - Обновить клиентскую сторону для сборки частей

2. **Улучшить архитектуру приложений**:
   - Создать систему зависимостей для приложений
   - Добавить версионирование приложений

3. **Добавить функциональность**:
   - Система уведомлений
   - Многозадачность
   - Система плагинов
