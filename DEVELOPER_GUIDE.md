# Руководство разработчика LoraCore OS

Это руководство поможет вам понять архитектуру виртуального компьютера LoraCore и научит создавать собственные операционные системы и приложения для внутриигрового планшета.

## Содержание

1. [Процесс загрузки (BIOS)](#процесс-загрузки-bios)
2. [Создание Java-ядра: Интерфейс IKernel](#создание-java-ядра-интерфейс-ikernel)
3. [API, доступное Java-ядру (IKernelApi)](#api-доступное-java-ядру-ikernelapi)
4. [Сборка и использование вашего Java-ядра](#сборка-и-использование-вашего-java-ядра)
5. [Создание Java-приложений: Интерфейс IApplication](#создание-java-приложений-интерфейс-iapplication)
6. [Lua API: Обзор](#lua-api-обзор)
7. [Lua API: Глобальные объекты (`os`, `term`, `fs` и др.)](#lua-api-глобальные-объекты-os-term-fs-и-др)
8. [Lua API: API Устройств (`tablet`)](#lua-api-api-устройств-tablet)
9. [Архитектура виртуальной машины](#архитектура-виртуальной-машины)
10. [Система событий](#система-событий)
11. [Виртуальные устройства](#виртуальные-устройства)
12. [Отладка и диагностика](#отладка-и-диагностика)
13. [Примеры](#примеры)

## Процесс загрузки (BIOS)

BIOS (Basic Input/Output System) - это первичный загрузчик, который запускается при включении планшета. Файл `bios.lua` находится в `/os/bios.lua` и выполняет следующие функции:

### Логика загрузки

1. **Инициализация экрана**: BIOS мгновенно отрисовывает интерфейс загрузки с логотипом "LoraBIOS v1.1"

2. **Поиск Java-ядра**: BIOS проверяет наличие файла `/boot/kernel.jar` в виртуальной файловой системе

3. **Два сценария загрузки**:
   - **Если найден `kernel.jar`**: Запускается Java-ядро через `os.boot_java("/boot/kernel.jar")`
   - **Если ядро не найдено**: Запускается режим восстановления через `/os/recovery.lua`

### Структура загрузочных файлов

```
/os/
├── bios.lua          # Первичный загрузчик
├── recovery.lua      # Скрипт восстановления
└── bin/              # Системные утилиты

/boot/
├── kernel.jar        # Java-ядро операционной системы
└── boot.conf         # Конфигурация загрузки
```

### Создание собственного BIOS

Для создания собственного BIOS вы можете:
- Модифицировать существующий `bios.lua`
- Добавить дополнительные проверки
- Реализовать меню выбора операционной системы
- Добавить диагностику оборудования

## Создание Java-ядра: Интерфейс IKernel

Java-ядро - это основная компонента операционной системы, которая управляет жизненным циклом приложений и взаимодействует с оборудованием планшета. Любое Java-ядро должно реализовывать интерфейс `IKernel`.

### Новые возможности в версии 1.3.0

В версии 1.3.0 добавлена поддержка Java-приложений через интерфейс `IApplication`:

- **`IApplication`** - Контракт для всех Java-приложений
- **`IApplicationApi`** - Безопасный API для приложений
- **`ApplicationApiImpl`** - Реализация безопасного API
- **`JarClassLoader`** - Загрузчик классов из JAR-файлов
- **`WindowManager`** - Управление активными приложениями

### Жизненный цикл ядра

#### `onBoot(IKernelApi api)`
- **Вызывается**: Один раз при загрузке ядра
- **Назначение**: Инициализация ядра, загрузка системных ресурсов, запуск базовых сервисов
- **Параметры**: `api` - объект для взаимодействия с планшетом и игрой

#### `onRender(int mouseX, int mouseY, float delta)`
- **Вызывается**: Каждый кадр (60 FPS)
- **Назначение**: Отрисовка интерфейса ядра и приложений
- **Параметры**: 
  - `mouseX, mouseY` - координаты мыши
  - `delta` - время между кадрами в секундах

#### `onTick()`
- **Вызывается**: Каждый игровой тик (20 TPS)
- **Назначение**: Обновление логики, не связанной с рендером (обработка событий, обновление состояния)

#### `onEvent(KernelEvent event)`
- **Вызывается**: При событиях ввода (нажатие клавиш, клики мыши)
- **Назначение**: Обработка пользовательского ввода
- **Параметры**: `event` - объект с информацией о событии

#### `onShutdown()`
- **Вызывается**: Перед выключением виртуальной машины
- **Назначение**: Сохранение состояния, освобождение ресурсов

#### `onApiUpdate(IKernelApi newApi)`
- **Вызывается**: Когда ядру необходимо обновить внутренние ссылки на API.
- **Назначение**: В основном используется для обновления графического контекста (`IKernelGraphics`) после изменения размера окна или других событий, которые могут сделать старый контекст недействительным. Ядро должно обновить свои внутренние ссылки на `IKernelApi` и его дочерние API (например, `graphics = newApi.getGraphics()`).
- **Параметры**: `newApi` - новый экземпляр API с актуальными ссылками.

#### `getState()`
- **Возвращает**: Текущее состояние ядра (enum)
- **Назначение**: Информация для рендерера о состоянии ядра

#### `getCheckResults()`
- **Возвращает**: `Map<String, Boolean>` - результаты системных проверок
- **Назначение**: Диагностическая информация для отладки

## API, доступное Java-ядру

Java-ядро получает доступ к трем основным API через объект `IKernelApi`, переданный в метод `onBoot()`.

### IKernelApi - Основной API

#### Файловая система
- **`getVfs()`**: Возвращает `IKernelVfs` для работы с виртуальной файловой системой

#### Управление Lua-приложениями
- **`runLuaScript(String path)`**: Запускает Lua-скрипт в изолированном окружении
  - **Возвращает**: `CompletableFuture<Boolean>` - результат запуска
  - **Пример**: `api.runLuaScript("/home/user/app.lua")`

- **`setLuaExecutor(Consumer<String> executor)`**: Устанавливает исполнитель Lua-кода
- **`sendToLua(int threadId, Object... message)`**: Отправляет сообщение в Lua-поток

#### Системная информация
- **`getTerminalSize()`**: Возвращает размер терминала `[ширина, высота]`

#### Управление виртуальной машиной
- **`reboot()`**: Перезагружает виртуальную машину
- **`shutdown()`**: Выключает виртуальную машину

#### Графика
- **`getGraphics()`**: Возвращает `IKernelGraphics` для рисования

#### Взаимодействие с устройствами
- **`invokeDevice(String deviceType, String methodName, Object... args)`**: Асинхронно вызывает метод на серверном устройстве.
  - **Назначение**: Позволяет ядру взаимодействовать с оборудованием, которое существует на стороне сервера, например, с редстоун-интерфейсом. Это открывает возможности для управления механизмами и получения данных из мира Minecraft.
  - **Параметры**:
    - `deviceType` (String): Тип устройства (например, `"redstone"`).
    - `methodName` (String): Имя метода для вызова (например, `"getPower"`).
    - `args` (Object...): Аргументы для метода.
  - **Возвращает**: `CompletableFuture<Object[]>` - Future, который завершится с массивом результатов от устройства.
  - **Пример**:
    ```java
    // Асинхронно получаем уровень редстоун-сигнала с северной стороны
    CompletableFuture<Object[]> future = api.invokeDevice("redstone", "getPower", "north");
    future.thenAccept(result -> {
        if (result != null && result.length > 0 && result[0] instanceof Integer) {
            int power = (Integer) result[0];
            System.out.println("Redstone power from north: " + power);
        }
    });
    ```

### IKernelGraphics - Графический API

**Важно**: Этот API работает с пиксельным буфером, а не напрямую с экраном Minecraft.

#### Управление кадрами
- **`beginFrame()`**: Начало отрисовки кадра
- **`endFrame()`**: Завершение отрисовки кадра

#### Рисование примитивов
- **`fill(int x1, int y1, int x2, int y2, int color)`**: Заливка прямоугольника
- **`drawString(String text, int x, int y, int color)`**: Рисование текста
- **`drawCenteredString(String text, int centerX, int y, int color)`**: Рисование центрированного текста
- **`getStringWidth(String text)`**: Получение ширины текста


#### Рисование по пикселям
- **`setPixel(int x, int y, int color)**: установка цвета отдельного пикселя
- **`getPixel(int x, int y)**: получение цвета пикселя
- **`getWidth()**: получение ширины экрана
- **`getHeight()**: получение высоты экрана

#### Трансформации
- **`translate(double x, double y, double z)`**: Смещение системы координат
- **`pushMatrix()`**: Сохранение текущей матрицы трансформации
- **`popMatrix()`**: Восстановление предыдущей матрицы трансформации

#### Отсечение (Scissor)
- **`enableScissor(int x, int y, int width, int height)`**: Включение отсечения по прямоугольнику
- **`disableScissor()`**: Отключение отсечения

#### Синхронизация
- **`flush()`**: Принудительная отрисовка накопленных команд

### IKernelVfs - Виртуальная файловая система

**Важно**: Все операции асинхронны и возвращают `CompletableFuture`. Это предотвращает блокировку ядра при операциях ввода-вывода.

#### Проверки файлов
- **`exists(String path)`**: Проверка существования файла/директории
- **`isDirectory(String path)`**: Проверка, является ли путь директорией

#### Чтение и запись
- **`readBytes(String path)`**: Асинхронное чтение файла
  - **Возвращает**: `CompletableFuture<Optional<byte[]>>`
- **`writeBytes(String path, byte[] data)`**: Асинхронная запись файла
  - **Возвращает**: `CompletableFuture<Boolean>`

#### Управление директориями
- **`makeDir(String path)`**: Создание директории (с родительскими)
- **`list(String path)`**: Получение списка файлов в директории
- **`delete(String path)`**: Удаление файла или пустой директории

## Сборка и использование вашего Java-ядра

### Структура проекта ядра

Для создания собственного Java-ядра создайте отдельный подпроект в папке `kernel/`:

```
kernel/
├── build.gradle          # Конфигурация сборки
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── yourcompany/
│                   └── yourkernel/
│                       └── YourKernel.java
└── README.md
```

### Конфигурация build.gradle

```gradle
plugins {
    id 'fabric-loom'
    id 'java-library'
}

version = project.rootProject.mod_version
group = project.rootProject.maven_group
archivesBaseName = 'your-kernel-name' // Имя вашего JAR-файла

// Loom необходим для доступа к зависимостям Minecraft
loom {
    // Эта пустая секция говорит Loom "просто подготовь зависимости, но не делай из этого мод"
}

dependencies {
    // ВАЖНО: Зависимость от основного мода для доступа к IKernel, IKernelApi и т.д.
    // Используйте `main` вместо `client`, так как интерфейсы теперь в общем коде.
    compileOnly(rootProject.sourceSets.main.output)

    // Стандартные зависимости для работы с Minecraft
    modCompileOnly "net.fabricmc.fabric-api:fabric-api:${project.rootProject.fabric_version}"
    minecraft "com.mojang:minecraft:${project.rootProject.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.rootProject.yarn_mappings}:v2"
}

// Указываем версию Java
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// В манифесте JAR-файла необходимо указать главный класс вашего ядра
jar {
    manifest {
        attributes(
            'Kernel-Main-Class': 'com.yourcompany.yourkernel.YourKernel'
        )
    }
}
```

### Важные моменты

1. **Главный класс**: Должен быть указан в манифесте как `Kernel-Main-Class`.
2. **Зависимости**: Используйте `compileOnly(rootProject.sourceSets.main.output)` для доступа к интерфейсам мода, как показано в примере выше.
3. **Имя JAR**: Укажите уникальное `archivesBaseName` для вашего ядра.

### Сборка ядра

```bash
# В папке kernel/
./gradlew build
```

Результат: `kernel/build/libs/your-kernel-name-1.0.0.jar`

### Установка ядра

Скопируйте JAR-файл в `/boot/kernel.jar` в виртуальной файловой системе планшета.

## Создание Java-приложений: Интерфейс IApplication

### Создание Java-приложения

Java-приложения должны реализовывать интерфейс `IApplication`:

```java
public class MyApp implements IApplication {
    private IApplicationApi api;
    private IKernelGraphics graphics;
    
    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        this.graphics = api.getGraphics();
        // Инициализация приложения
    }
    
    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        // Отрисовка интерфейса
        g.beginFrame();
        g.fill(0, 0, 480, 270, 0x1E1E1E); // Черный фон
        g.drawCenteredString("My Application", 240, 135, 0xF0F0F0);
        g.endFrame();
    }
    
    @Override
    public void onEvent(KernelEvent event) {
        // Обработка событий
        switch (event) {
            case KernelEvent.KeyPressed keyEvent -> {
                if (keyEvent.keyCode() == GLFW.GLFW_KEY_ESCAPE) {
                    // Закрытие приложения
                }
            }
            case KernelEvent.MouseClicked mouseEvent -> {
                // Обработка клика мыши
            }
        }
    }
    
    @Override
    public void onClose() {
        // Очистка ресурсов
    }
}
```

### API для приложений

Приложения получают доступ к безопасному API через `IApplicationApi`:

- **`getVfs()`** - доступ к файловой системе
- **`getGraphics()`** - графический API
- **`askAI(String prompt)`** - запрос к ИИ
- **`getScreenSize()`** - размер экрана
- **`runLuaScript(String path)`** - запуск Lua-скриптов

### Сборка и установка

1. Создайте манифест `META-INF/MANIFEST.MF`:
```
Manifest-Version: 1.0
App-Main-Class: com.example.MyApp
```

2. Упакуйте в JAR-файл и поместите в `/home/user/apps/`

## Lua API: Обзор

LoraCore предоставляет две основные модели доступа к функциям планшета из Lua:

1.  **Глобальные объекты**: Простые в использовании объекты, такие как `os`, `term`, `fs`, которые доступны в глобальной области видимости. Они предназначены для базовых операций и совместимости со старыми скриптами.
2.  **API Устройств (`tablet`)**: Более современный, объектно-ориентированный подход. Все аппаратные компоненты представлены как устройства в глобальной таблице `tablet` (например, `tablet.gpu`, `tablet.terminal`). Этот подход является более гибким и расширяемым.

Для новых приложений рекомендуется использовать API Устройств (`tablet.*`), так как он предоставляет наиболее полный и актуальный функционал.

## Lua API: Глобальные объекты (`os`, `term`, `fs` и др.)

Эти API доступны как глобальные переменные в любом Lua-скрипте, что делает их удобными для быстрых и простых программ.

### Глобальные переменные

#### `os` - Операционная система
- **`os.sleep(seconds)`**: Приостанавливает выполнение программы на указанное время
  - **Параметры**: `seconds` (number) - время в секундах
  - **Пример**: `os.sleep(1.5)` - пауза на 1.5 секунды

- **`os.pullEvent([filter])`**: Ожидает и возвращает следующее событие
  - **Параметры**: `filter` (string, опционально) - фильтр по типу события
  - **Возвращает**: Событие и его параметры
  - **Пример**: `local event, param1, param2 = os.pullEvent("key")`

- **`os.reboot()`**: Перезагружает виртуальную машину
- **`os.shutdown()`**: Выключает виртуальную машину
- **`os.boot_java(path)`**: Загружает Java-ядро из указанного пути
  - **Параметры**: `path` (string) - путь к JAR-файлу ядра

#### `fs` - Файловая система (Асинхронная)

**Важно**: Все операции `fs` асинхронны и требуют использования корутин (`coroutine.yield`).

- **`fs.exists(path)`**: Проверяет существование файла или директории
  - **Параметры**: `path` (string) - путь к файлу
  - **Возвращает**: `true` если файл существует, `false` иначе

- **`fs.read(path)`**: Читает содержимое файла
  - **Параметры**: `path` (string) - путь к файлу
  - **Возвращает**: Содержимое файла или `nil` и сообщение об ошибке

- **`fs.write(path, content)`**: Записывает данные в файл
  - **Параметры**: 
    - `path` (string) - путь к файлу
    - `content` (string) - содержимое для записи
  - **Возвращает**: `true` при успехе, `nil` и сообщение об ошибке

- **`fs.isDir(path)`**: Проверяет, является ли путь директорией
- **`fs.list(path)`**: Получает список файлов в директории
- **`fs.makeDir(path)`**: Создает директорию

**Пример использования асинхронного API:**
```lua
local function readFile(path)
    local content, error = fs.read(path)
    if not content then
        print("Ошибка: " .. error)
        return
    end
    print("Содержимое: " .. content)
end
```

#### `term` - Терминал

**Примечание:** Помимо глобального объекта `term`, все те же функции доступны через `tablet.terminal`. Использование `tablet.terminal` является предпочтительным в новых приложениях, так как это соответствует современной архитектуре устройств.

- **`term.write(text)`**: Записывает текст в текущей позиции курсора
- **`term.print(text)`**: Записывает текст и переводит строку
- **`term.clear()`**: Очищает весь экран терминала
- **`term.clearLine()`**: Очищает текущую строку
- **`term.setCursorPos(x, y)`**: Устанавливает позицию курсора
- **`term.getCursorPos()`**: Возвращает текущую позицию курсора `{x, y}`
- **`term.getSize()`**: Возвращает размер терминала `{width, height}`
- **`term.read()`**: Читает строку ввода от пользователя
- **`term.setTextColor(color)`**: Устанавливает цвет текста
- **`term.setBackgroundColor(color)`**: Устанавливает цвет фона
- **`term.setCursorBlink(enabled)`**: Включает/выключает мигание курсора

#### `gpu` - Графический процессор

**Важно:** Начиная с новой версии, глобальный объект `gpu` был удален в пользу `tablet.gpu`. Все функции остались прежними, но теперь их нужно вызывать через API устройств.

**Пример:**
```lua
-- Старый код:
-- gpu.fill(0, 0, 10, 10, colors.red)

-- Новый код:
tablet.gpu.fill(0, 0, 10, 10, colors.red)
```

Функции, доступные в `tablet.gpu`, будут подробно описаны в разделе [API Устройств (`tablet`)](#lua-api-api-устройств-tablet).

#### `colors` - Цветовая палитра

Доступны все 16 стандартных цветов Minecraft:
- `colors.white`, `colors.orange`, `colors.magenta`, `colors.lightBlue`
- `colors.yellow`, `colors.lime`, `colors.pink`, `colors.gray`
- `colors.lightGray`, `colors.cyan`, `colors.purple`, `colors.blue`
- `colors.brown`, `colors.green`, `colors.red`, `colors.black`

#### `bios` - BIOS API

- **`bios.getRecovery()`**: Возвращает содержимое скрипта восстановления
- **`bios.getInstaller()`**: Возвращает содержимое установщика

#### `tablet` - API планшета

- **`tablet.gpu`**: Доступ к графическому API планшета
- **`tablet.terminal`**: Доступ к терминалу планшета

#### `print` - Функция вывода

- **`print(...)`**: Выводит аргументы в терминал с переводом строки
  - **Пример**: `print("Hello", "World", 42)`

#### `require` - Загрузка модулей

- **`require(module)`**: Загружает Lua-модуль
  - **Параметры**: `module` (string) - имя модуля
  - **Пример**: `local myModule = require("myModule")`

#### `loadfile` - Загрузка файлов

- **`loadfile(path)`**: Загружает и компилирует Lua-файл
  - **Параметры**: `path` (string) - путь к файлу
  - **Возвращает**: Функция или `nil` и сообщение об ошибке

## Lua API: API Устройств (`tablet`)

Это основной и рекомендуемый способ взаимодействия с оборудованием планшета. Каждое "железное" или виртуальное устройство представлено как таблица внутри глобального объекта `tablet`.

### `tablet.cpu` - Центральный процессор
- **`getTime()`**: Возвращает время работы виртуальной машины в секундах.
- **`getArchitecture()`**: Возвращает архитектуру процессора (например, "Lua 5.4").

### `tablet.ram` - Оперативная память
- **`getTotalSize()`**: Возвращает общий объем доступной памяти в КБ.
- **`getUsedSize()`**: Возвращает текущий объем используемой памяти в КБ.

### `tablet.gpu` - Графический процессор
- **`fill(x, y, width, height, color)`**: Заливает прямоугольную область цветом.
- **`drawText(x, y, text, color)`**: Рисует текст в указанной позиции.
- **`copy(x, y, width, height, toX, toY)`**: Копирует область экрана.

### `tablet.terminal` - Терминал
Предоставляет тот же набор функций, что и глобальный объект `term`.
- **`write(text)`**, **`print(text)`**, **`clear()`**, **`clearLine()`**, **`setCursorPos(x, y)`**, **`getCursorPos()`**, **`getSize()`**, **`setTextColor(color)`**, **`setBackgroundColor(color)`**, **`setCursorBlink(enabled)`**, **`read()`**.

### `tablet.thread` - Управление потоками
- **`create(code, globals)`**: Создает и запускает новый Lua-поток.
- **`send(threadId, ...)`**: Отправляет сообщение в указанный поток.

### `tablet.redstone` - Редстоун-интерфейс (НОВОЕ)
Позволяет взаимодействовать с редстоун-сигналами блока, к которому подключен планшет. Это API работает на стороне сервера.
- **`getPower(side)`**: Возвращает уровень редстоун-сигнала (0-15), который блок **получает** с указанной стороны.
  - **Параметры**: `side` (string) - сторона, одна из: `"north"`, `"south"`, `"east"`, `"west"`, `"up"`, `"down"`.
  - **Пример**:
    ```lua
    local power = tablet.redstone.getPower("north")
    if power > 0 then
        print("Сигнал с севера: " .. power)
    end
    ```

## Архитектура виртуальной машины

### Основные компоненты

#### VirtualMachine
Центральный класс, управляющий жизненным циклом виртуальной машины:

- **Конструктор**: `VirtualMachine(architecture, totalRamKb, terminal, resourceLoader, vfs, fsUuid, tabletUuid, javaBootHandler)`
- **Архитектура**: Поддерживаемые архитектуры процессора
- **Память**: Общий объем RAM в килобайтах
- **Терминал**: Интерфейс для ввода-вывода
- **Ресурсы**: Загрузчик системных файлов
- **VFS**: Виртуальная файловая система
- **UUID**: Уникальные идентификаторы для файловой системы и планшета

#### LuaThreadRunner
Управляет выполнением Lua-кода в отдельных потоках:

- **Многопоточность**: Каждый Lua-скрипт выполняется в отдельном потоке
- **События**: Очередь событий для каждого потока
- **Корутины**: Поддержка Lua-корутин для асинхронных операций
- **Обработка ошибок**: Автоматическое восстановление после сбоев

#### ResourceLoader
Функциональный интерфейс для загрузки системных ресурсов:

```java
@FunctionalInterface
public interface ResourceLoader {
    String load(String path);
}
```

### Жизненный цикл VM

1. **Инициализация**: Создание устройств, настройка API
2. **Загрузка**: Выполнение BIOS и загрузка ядра
3. **Выполнение**: Основной цикл обработки событий
4. **Завершение**: Очистка ресурсов и корректное завершение

### Система потоков

- **Главный поток (ID: 0)**: Выполняет основной скрипт загрузки
- **Дочерние потоки**: Создаются для параллельного выполнения задач
- **Синхронизация**: Потоки могут обмениваться сообщениями

## Система событий

### KernelEvent - События ядра

`KernelEvent` - запечатанный класс, представляющий все возможные события ввода:

#### События клавиатуры
- **`KeyPressed`**: Нажатие клавиши
  - `keyCode`: Код клавиши
  - `scanCode`: Скан-код
  - `modifiers`: Модификаторы (Shift, Ctrl, Alt)

- **`KeyReleased`**: Отпускание клавиши
- **`CharTyped`**: Ввод символа
  - `chr`: Символ
  - `modifiers`: Модификаторы

#### События мыши
- **`MouseClicked`**: Нажатие кнопки мыши
  - `mouseX, mouseY`: Координаты курсора
  - `button`: Номер кнопки

- **`MouseReleased`**: Отпускание кнопки мыши
- **`MouseScrolled`**: Прокрутка колеса мыши
  - `horizontalAmount, verticalAmount`: Направление прокрутки

### Обработка событий в Java-ядре

```java
@Override
public void onEvent(KernelEvent event) {
    switch (event) {
        case KernelEvent.KeyPressed keyEvent -> {
            // Обработка нажатия клавиши
            if (keyEvent.keyCode() == GLFW.GLFW_KEY_ESCAPE) {
                // Действие при нажатии Escape
            }
        }
        case KernelEvent.MouseClicked mouseEvent -> {
            // Обработка клика мыши
            handleMouseClick(mouseEvent.mouseX(), mouseEvent.mouseY());
        }
        // Обработка других типов событий...
    }
}
```

### События в Lua

Lua-скрипты могут получать события через `os.pullEvent()`:

```lua
while true do
    local event, param1, param2, param3 = os.pullEvent()
    
    if event == "key" then
        -- Обработка события клавиатуры
        local keyCode = param1
        local isPressed = param2
    elseif event == "mouse_click" then
        -- Обработка клика мыши
        local x, y = param1, param2
        local button = param3
    end
end
```

## Виртуальные устройства

### Архитектура устройств

Система устройств LoraCore построена на Java-классах, методы которых "пробрасываются" в Lua с помощью аннотации `@Callback`. Это позволяет легко расширять функционал, добавляя новые виртуальные устройства.

- **`IDevice`**: (Серверная сторона) Интерфейс для устройств, которые должны взаимодействовать с миром Minecraft, как например `RedstoneDevice`.
- **`@Callback`**: Аннотация для Java-методов, которые должны быть доступны из Lua. Позволяет указать имя функции в Lua и ее документацию.

### Список стандартных устройств

Ниже представлен список устройств, доступных в стандартной сборке LoraCore.

#### Клиентские устройства
Эти устройства работают на стороне клиента и в основном отвечают за интерфейс и управление потоками.

- **`CpuDevice` (`tablet.cpu`)**: Информация о процессоре.
- **`RamDevice` (`tablet.ram`)**: Информация о памяти.
- **`GpuDevice` (`tablet.gpu`)**: Управление графикой.
- **`TerminalDevice` (`tablet.terminal`)**: Управление терминалом.
- **`ThreadDevice` (`tablet.thread`)**: Управление потоками.

#### Серверные устройства
Эти устройства работают на стороне сервера и требуют асинхронного вызова через `IKernelApi.invokeDevice()` из Java-ядра.

- **`RedstoneDevice` (`tablet.redstone`)**: Взаимодействие с редстоун-сигналами. Позволяет Java-ядрам и Lua-скриптам читать состояние редстоун-цепей, к которым подключен планшет.

### Создание собственных устройств

Для создания нового виртуального устройства:

1. **Создайте класс устройства**:
```java
public class MyDevice {
    @Callback(value = "myFunction", doc = "Описание функции")
    public String myFunction(String param) {
        return "Result: " + param;
    }
}
```

2. **Добавьте устройство в VirtualMachine**:
```java
this.devices.add(new MyDevice());
```

3. **Используйте в Lua**:
```lua
local result = tablet.mydevice.myFunction("test")
print(result) -- Выведет: Result: test
```

### Аннотация @Callback

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Callback {
    String value() default "";  // Имя функции в Lua
    String doc() default "";    // Документация
}
```

## Отладка и диагностика

### Debug API

Доступен через глобальную переменную `debug`:

- **`debug.printError(message)`**: Вывод ошибки в терминал красным цветом

### Системная диагностика

#### Состояние виртуальной машины
- **`vm.isRunning()`**: Проверка работоспособности VM
- **`vm.getCrashMessage()`**: Получение сообщения о сбое
- **`vm.getUptime()`**: Время работы в секундах
- **`vm.getMemoryUsage()`**: Использование памяти

#### Обработка сбоев
```java
// В Java-ядре
@Override
public Map<String, Boolean> getCheckResults() {
    Map<String, Boolean> checks = new HashMap<>();
    checks.put("vm_running", vm.isRunning());
    checks.put("memory_ok", vm.getMemoryUsage() < 1000);
    checks.put("disk_space", vfs.exists("/").join());
    return checks;
}
```

### Логирование

LoraCore использует стандартную систему логирования:

```java
LoraCoreMod.LOGGER.info("Информационное сообщение");
LoraCoreMod.LOGGER.warn("Предупреждение");
LoraCoreMod.LOGGER.error("Ошибка", exception);
```

### RebootSignalException

Специальное исключение для корректного завершения работы:

```java
// В Lua API
public void reboot() {
    vm.reboot();
    throw new RebootSignalException(); // Корректное завершение
}
```

### Мониторинг производительности

#### Метрики VM
- **Время работы**: `vm.getUptime()`
- **Использование памяти**: `vm.getMemoryUsage()`
- **Количество потоков**: `threads.size()`
- **Состояние потоков**: `thread.isAlive()`

#### Оптимизация
- Используйте асинхронные операции для I/O
- Избегайте блокирующих вызовов в основном потоке
- Правильно управляйте ресурсами (память, файлы)
- Используйте корутины для сложной логики

## Примеры

### "Hello World" на Java

Простой класс, реализующий `IKernel`:

```java
package com.example.kernel;

import com.loracore.computer.kernel.*;

public class HelloWorldKernel implements IKernel {
    private IKernelApi api;
    private IKernelGraphics graphics;
    
    @Override
    public void onBoot(IKernelApi api) {
        this.api = api;
        this.graphics = api.getGraphics();
        
        // Рисуем приветствие
        graphics.beginFrame();
        graphics.fill(0, 0, 480, 270, 0x1E1E1E); // Черный фон
        graphics.drawCenteredString("Hello from Java!", 240, 135, 0xF0F0F0); // Белый текст
        graphics.endFrame();
    }
    
    @Override
    public void onRender(int mouseX, int mouseY, float delta) {
        // Отрисовка обновляется каждый кадр
    }
    
    @Override
    public void onTick() {
        // Логика обновляется каждый тик
    }
    
    @Override
    public void onEvent(KernelEvent event) {
        // Обработка событий
    }
    
    @Override
    public void onShutdown() {
        // Очистка ресурсов
    }
    
    @Override
    public Object getState() {
        return "RUNNING";
    }
    
    @Override
    public java.util.Map<String, Boolean> getCheckResults() {
        return new java.util.HashMap<>();
    }
}
```

### "Hello World" на Lua

Простой `boot.lua`:

```lua
-- Очищаем экран
term.clear()

-- Устанавливаем цвет текста
term.setTextColor(colors.white)

-- Выводим приветствие
term.write("Hello from Lua!")
term.print("") -- Переводим строку

-- Ждем ввода пользователя
term.write("Press Enter to continue...")
local input = term.read()

-- Очищаем экран и выходим
term.clear()
print("Goodbye!")
```

### Создание простого приложения

Пример приложения-калькулятора на Lua:

```lua
-- calculator.lua
local function calculator()
    term.clear()
    term.setCursorPos(1, 1)
    print("=== Calculator ===")
    
    while true do
        term.write("Enter first number: ")
        local a = tonumber(term.read())
        
        term.write("Enter operation (+,-,*,/): ")
        local op = term.read()
        
        term.write("Enter second number: ")
        local b = tonumber(term.read())
        
        local result
        if op == "+" then
            result = a + b
        elseif op == "-" then
            result = a - b
        elseif op == "*" then
            result = a * b
        elseif op == "/" then
            result = a / b
        else
            print("Unknown operation!")
            goto continue
        end
        
        print("Result: " .. result)
        
        ::continue::
        term.write("Continue? (y/n): ")
        if term.read() ~= "y" then
            break
        end
    end
    
    print("Goodbye!")
end

calculator()
```

## Продвинутые примеры

### Многопоточное приложение на Lua

```lua
-- multithread_demo.lua
local function worker(threadId)
    print("Worker " .. threadId .. " started")
    
    for i = 1, 5 do
        os.sleep(1)
        print("Worker " .. threadId .. ": iteration " .. i)
    end
    
    print("Worker " .. threadId .. " finished")
end

-- Создаем несколько потоков
local threads = {}
for i = 1, 3 do
    local threadId = tablet.thread.create([[
        local threadId = ]] .. i .. [[
        local function worker(id)
            print("Worker " .. id .. " started")
            for i = 1, 5 do
                os.sleep(1)
                print("Worker " .. id .. ": iteration " .. i)
            end
            print("Worker " .. id .. " finished")
        end
        worker(threadId)
    ]], _G)
    
    if threadId then
        table.insert(threads, threadId[1])
    end
end

print("Main thread: waiting for workers...")
os.sleep(6)
print("Main thread: all workers should be done")
```

### Графическое приложение

```lua
-- graphics_demo.lua
local function drawButton(x, y, width, height, text, color)
    -- Фон кнопки
    tablet.gpu.fill(x, y, width, height, colors.gray)
    -- Рамка
    tablet.gpu.fill(x, y, width, 2, colors.white)
    tablet.gpu.fill(x, y, 2, height, colors.white)
    tablet.gpu.fill(x + width - 2, y, 2, height, colors.white)
    tablet.gpu.fill(x, y + height - 2, width, 2, colors.white)
    -- Текст
    tablet.gpu.drawText(x + 5, y + height/2 - 5, text, color)
end

local function drawWindow(x, y, width, height, title)
    -- Фон окна
    tablet.gpu.fill(x, y, width, height, colors.lightGray)
    -- Заголовок
    tablet.gpu.fill(x, y, width, 20, colors.blue)
    tablet.gpu.drawText(x + 5, y + 5, title, colors.white)
    -- Кнопка закрытия
    tablet.gpu.fill(x + width - 25, y + 2, 20, 16, colors.red)
    tablet.gpu.drawText(x + width - 18, y + 5, "X", colors.white)
end

-- Очищаем экран
tablet.gpu.fill(0, 0, 480, 270, colors.black)

-- Рисуем окно
drawWindow(50, 50, 200, 150, "My Application")

-- Рисуем кнопки
drawButton(70, 100, 80, 30, "OK", colors.green)
drawButton(160, 100, 80, 30, "Cancel", colors.red)

print("Graphics demo completed!")
```

### Файловый менеджер

```lua
-- file_manager.lua
local function listDirectory(path)
    local files, error = fs.list(path or "/")
    if not files then
        print("Error: " .. error)
        return
    end
    
    print("Directory: " .. (path or "/"))
    print("Files:")
    
    for i, file in ipairs(files) do
        local fullPath = (path or "/") .. "/" .. file
        local isDir, dirError = fs.isDir(fullPath)
        
        if isDir then
            print("  [DIR]  " .. file)
        else
            print("  [FILE] " .. file)
        end
    end
end

local function readFile(path)
    local content, error = fs.read(path)
    if not content then
        print("Error reading file: " .. error)
        return
    end
    
    print("File: " .. path)
    print("Content:")
    print(content)
end

local function writeFile(path, content)
    local success, error = fs.write(path, content)
    if not success then
        print("Error writing file: " .. error)
        return
    end
    
    print("File written successfully: " .. path)
end

-- Основной цикл
while true do
    term.clear()
    print("=== File Manager ===")
    print("1. List directory")
    print("2. Read file")
    print("3. Write file")
    print("4. Exit")
    
    term.write("Choose option: ")
    local choice = term.read()
    
    if choice == "1" then
        term.write("Enter path (or press Enter for root): ")
        local path = term.read()
        listDirectory(path ~= "" and path or nil)
    elseif choice == "2" then
        term.write("Enter file path: ")
        local path = term.read()
        readFile(path)
    elseif choice == "3" then
        term.write("Enter file path: ")
        local path = term.read()
        term.write("Enter content: ")
        local content = term.read()
        writeFile(path, content)
    elseif choice == "4" then
        break
    end
    
    term.write("Press Enter to continue...")
    term.read()
end

print("Goodbye!")
```

### Сетевое приложение (имитация)

```lua
-- network_demo.lua
local function simulateNetworkRequest(url)
    print("Connecting to: " .. url)
    os.sleep(1) -- Имитация задержки сети
    
    -- Имитация ответа
    local responses = {
        ["http://example.com"] = "Hello from example.com!",
        ["http://api.test"] = '{"status": "ok", "data": "test"}',
        ["http://weather.com"] = "Temperature: 22°C, Sunny"
    }
    
    local response = responses[url] or "404 Not Found"
    print("Response: " .. response)
    return response
end

local function httpGet(url)
    print("HTTP GET " .. url)
    return simulateNetworkRequest(url)
end

local function httpPost(url, data)
    print("HTTP POST " .. url)
    print("Data: " .. data)
    return simulateNetworkRequest(url)
end

-- Демонстрация
print("=== Network Demo ===")

local response1 = httpGet("http://example.com")
local response2 = httpGet("http://api.test")
local response3 = httpPost("http://weather.com", "location=moscow")

print("All requests completed!")
```

## Лучшие практики

### Производительность

1. **Используйте асинхронные операции**:
   ```lua
   -- Плохо (блокирующий)
   local content = fs.read("/large_file.txt")
   
   -- Хорошо (асинхронный)
   local content = fs.read("/large_file.txt")
   -- Используйте корутины для обработки результата
   ```

2. **Избегайте бесконечных циклов**:
   ```lua
   -- Плохо
   while true do
       -- Тяжелые операции
   end
   
   -- Хорошо
   while true do
       os.sleep(0.1) -- Даем время другим процессам
       -- Операции
   end
   ```

3. **Правильно управляйте памятью**:
   ```lua
   -- Очищайте большие таблицы
   local data = {}
   -- ... работа с данными ...
   data = nil
   collectgarbage("collect")
   ```

### Безопасность

1. **Проверяйте входные данные**:
   ```lua
   local function safeReadFile(path)
       if not path or path == "" then
           return nil, "Invalid path"
       end
       
       -- Проверяем, что путь не содержит опасные символы
       if path:find("..") then
           return nil, "Path traversal not allowed"
       end
       
       return fs.read(path)
   end
   ```

2. **Обрабатывайте ошибки**:
   ```lua
   local function robustOperation()
       local success, result = pcall(function()
           return riskyOperation()
       end)
       
       if not success then
           print("Error: " .. result)
           return nil
       end
       
       return result
   end
   ```

### Совместимость

1. **Используйте правильные API для вашей задачи**:
   ```lua
   -- Для новых приложений рекомендуется использовать API устройств
   tablet.terminal.write("Hello")
   os.sleep(1) -- os.sleep является стандартной функцией, у tablet.cpu нет метода sleep

   -- Глобальные API (term, fs) полезны для простых скриптов или обратной совместимости.
   ```

2. **Проверяйте доступность функций**:
   ```lua
   if fs then
       -- Файловая система доступна
       local content = fs.read("/file.txt")
   else
       print("File system not available")
   end
   ```

### Документация

1. **Документируйте функции**:
   ```lua
   --- Читает файл и возвращает его содержимое
   -- @param path Путь к файлу
   -- @return Содержимое файла или nil и сообщение об ошибке
   local function readFile(path)
       -- Реализация
   end
   ```

2. **Создавайте README для приложений**:
   ```markdown
   # My Application
   
   ## Описание
   Краткое описание приложения
   
   ## Использование
   Инструкции по использованию
   
   ## API
   Описание доступных функций
   
   ## Примеры
   Примеры использования
   ```

## Заключение

### Что вы узнали

Это руководство предоставило вам полное понимание архитектуры LoraCore OS:

1. **Процесс загрузки**: От BIOS до запуска Java-ядра или Lua-приложений
2. **Java-разработка**: Создание собственных ядер с полным API
3. **Lua-разработка**: Создание приложений с богатым набором API
4. **Архитектура VM**: Понимание внутренней работы виртуальной машины
5. **Система событий**: Обработка пользовательского ввода
6. **Виртуальные устройства**: Расширение функциональности
7. **Отладка**: Диагностика и мониторинг производительности

### Следующие шаги

1. **Изучите примеры**: Запустите и модифицируйте предоставленные примеры
2. **Создайте простое приложение**: Начните с базового "Hello World"
3. **Изучите исходный код**: Исследуйте реализацию существующих компонентов
4. **Присоединитесь к сообществу**: Делитесь своими проектами и получайте обратную связь

### Полезные ресурсы

- **Lua документация**: https://www.lua.org/manual/
- **LuaJ**: https://github.com/luaj/luaj
- **Fabric API**: https://fabricmc.net/wiki/
- **Minecraft Forge**: https://mcforge.readthedocs.io/

### Поддержка

Если у вас возникли вопросы или проблемы:

1. Проверьте логи игры на наличие ошибок
2. Убедитесь, что все зависимости установлены
3. Проверьте совместимость версий
4. Обратитесь к сообществу разработчиков

---

**Удачной разработки! 🚀**

Создавайте удивительные операционные системы и приложения для внутриигрового планшета LoraCore!
