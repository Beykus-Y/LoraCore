# Руководство по разработке ПО для LoraOS

Этот документ описывает, как написать и упаковать Java‑приложение для виртуальной ОС LoraOS, какие интерфейсы доступны разработчику и как рисовать UI без AWT/Swing.

## 1. Формат Java‑приложения и .jar

- Приложение — это обычный Java‑класс, реализующий интерфейс `com.lora.tabletos.core.IApplication`.
- Класс должен иметь публичный конструктор без аргументов.
- Приложение поставляется в виде отдельного `.jar`, который загружается ядром LoraOS из виртуальной файловой системы планшета.
- В `MANIFEST.MF` обязательно должно быть указано свойство:

  ```text
  App-Main-Class: com.example.myos.HelloWorldApp
  ```

  где `com.example.myos.HelloWorldApp` — ваш класс, реализующий `IApplication`.

### 1.1. Минимальная структура проекта

Рекомендуемая структура Maven/Gradle‑проекта:

- `src/main/java/com/example/myos/HelloWorldApp.java` — основной класс приложения.
- `src/main/resources/META-INF/MANIFEST.MF` — манифест с полем `App-Main-Class`.

При сборке Gradle‑проекта достаточно подключить ядро как `compileOnly`:

- `compileOnly` на `kernel.jar` (содержит `IApplication`, `IApplicationApi`).
- `compileOnly` на основной мод (`loracore.jar`), если нужны дополнительные типы.

В результате на выходе должен быть один `.jar`, который можно скопировать в виртуальную файловую систему планшета и запускать через LoraOS.

## 2. Жизненный цикл IApplication

Интерфейс `com.lora.tabletos.core.IApplication` определяет жизненный цикл Java‑приложения:

- `void onLoad(IApplicationApi api)`  
  Вызывается один раз при загрузке приложения. Здесь вы сохраняете ссылку на `api`, инициализируете состояние, загружаете ресурсы.

- `void onResume()`  
  Приложение стало активным и отображается на экране. Вызывается после `onLoad`, а также при возврате к приложению из фонового состояния.

- `void onPause()`  
  Приложение уходит в фон (пользователь переключился на другое приложение или на рабочий стол). Здесь можно останавливать тяжелые операции, таймеры и фоновые задачи.

- `void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta)`  
  Вызывается каждый кадр отрисовки.  
  Параметры:
  - `g` — графический контекст ядра (`IKernelGraphics`).
  - `mouseX`, `mouseY` — текущие координаты курсора (в пикселях, координаты экрана LoraOS).
  - `delta` — время между кадрами в секундах (float).

  В этом методе вы полностью рисуете UI приложения на виртуальном экране.

- `boolean onEvent(KernelEvent event)`  
  Вызывается при событиях ввода (клавиатура, мышь, системные сигналы).  
  Верните `true`, если событие обработано, и `false`, если его можно передать дальше.

- `void onClose()`  
  Вызывается при закрытии приложения. Здесь освобождаются ресурсы и сохраняется состояние.

## 3. Возможности IApplicationApi

Интерфейс `com.lora.tabletos.core.IApplicationApi` — безопасный фасад над внутренним `IKernelApi`. Приложение получает только тот функционал, который нужен для UI и логики, без прямого доступа к Minecraft‑миру.

Ключевые возможности:

- `IKernelVfs getVfs()`  
  Доступ к виртуальной файловой системе планшета.  
  Позволяет:
  - читать и писать файлы,
  - создавать директории,
  - перечислять содержимое каталогов.

- `IKernelGraphics getGraphics()`  
  Возвращает текущий графический контекст. Обычно он передается в `onRender`, но доступ к нему можно получить и через API при необходимости.

- `CompletableFuture<String> askAI(String prompt)`  
  Отправить запрос к ИИ и асинхронно получить текстовый ответ. Удобно для приложений‑ассистентов, чат‑ботов, генерации кода и подсказок.

- `int[] getScreenSize()`  
  Возвращает `[width, height]` виртуального экрана в пикселях. Используйте это для адаптивной разметки UI.

- `CompletableFuture<Boolean> runLuaScript(String path)`  
  Запустить Lua‑скрипт из виртуальной файловой системы в изолированном окружении.  
  Можно использовать для гибких плагинов поверх Java‑приложения.

- `void showNotification(String message, boolean isError)`  
  Показать системное уведомление LoraOS. Если `isError == true`, используется оформление ошибки, иначе обычное информационное.

- `void runOnRenderThread(Runnable task)`  
  Безопасно выполнить задачу в потоке отрисовки клиента. Используйте для операций, которые должны взаимодействовать с графикой Minecraft или с клиентскими ресурсами.

- `CompletableFuture<Object[]> invokeDevice(String deviceType, String methodName, Object... args)`  
  Асинхронно вызвать метод на серверном устройстве (например, редстоун, датчики и т.п.).  
  Результат приходит как массив объектов, который приложение декодирует самостоятельно.

- `java.util.Map<String, Double> getSystemMetrics()`  
  Возвращает карту метрик виртуальной машины:
  - `"cpu_load"` — загрузка CPU от `0.0` до `1.0`.
  - `"ram_used_kb"` — использованная память в КБ.
  - `"ram_total_kb"` — общий лимит памяти в КБ.
  - `"disk_queue"` — размер очереди задач диска.
  - `"uptime"` — аптайм виртуальной машины в секундах (как `Double`, фактически целое значение).

- `String getTabletUuidStr()`  
  Строковый UUID планшета (для отображения в UI, логирования или телеметрии).

- `String getFsUuidStr()`  
  Строковый UUID файловой системы планшета.

## 4. Графика без AWT/Swing: IKernelGraphics

Внутри LoraOS нет AWT/Swing. Вместо этого используется абстракция `com.loracore.computer.kernel.IKernelGraphics`, которая рисует прямо в текстуру планшета.

Основные методы, которые нужны большинству приложений:

- `void fill(int x1, int y1, int x2, int y2, int color)`  
  Заливает прямоугольник цветом `color` (формат `0xAARRGGBB`).  
  Обычно используется для фона, панелей, полос прогресса.

- `void drawString(String text, int x, int y, int color)`  
  Рисует текст строкой стандартного размера.

- `void drawString(String text, int x, int y, int color, float scale)`  
  Рисует текст с указанным масштабом (`1.0f` — нормальный размер, `2.0f` — крупный заголовок).

Дополнительные полезные методы:

- `void drawCenteredString(String text, int centerX, int y, int color)` — рисование строки по центру.
- `int getStringWidth(String text)` — ширина текста в пикселях, помогает при выравнивании.
- `void beginFrame()` и `void endFrame()` — границы кадра, если нужно вручную управлять пакетами отрисовки.
- `void enableScissor(int x, int y, int width, int height)` / `void disableScissor()` — ограничение области рисования (скроллинг, окна).
- `void setPixel(int x, int y, int color)` / `int getPixel(int x, int y)` — низкоуровневый доступ к пикселям.

### 4.1. Базовый шаблон отрисовки

Типичный `onRender` для простого приложения:

```java
public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
    int[] size = api.getScreenSize();
    int width = size[0];
    int height = size[1];

    g.fill(0, 0, width, height, 0xFF000000);
    g.drawString("Hello from LoraOS", 20, 40, 0xFFFFFFFF);
}
```

## 5. Пример: приложение «Hello World»

Ниже приведен минимальный пример приложения, которое заполняет экран цветом и пишет «Hello World» по центру.

```java
package com.example.myos;

import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;

public class HelloWorldApp implements IApplication {
    private IApplicationApi api;

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] size = api.getScreenSize();
        int width = size[0];
        int height = size[1];

        g.fill(0, 0, width, height, 0xFF1A1A2E);

        String text = "Hello, LoraOS!";
        int textWidth = g.getStringWidth(text);
        int x = (width - textWidth) / 2;
        int y = height / 2;

        g.drawString(text, x, y, 0xFFFFFFFF);
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        return false;
    }

    @Override
    public void onClose() {
    }
}
```

### 5.1. Манифест для HelloWorldApp

`src/main/resources/META-INF/MANIFEST.MF`:

```text
Manifest-Version: 1.0
App-Main-Class: com.example.myos.HelloWorldApp
```

После сборки положите получившийся `.jar` в виртуальную файловую систему планшета (например, в `/apps/hello.jar`) и запустите его через соответствующий лаунчер в LoraOS.

