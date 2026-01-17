package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.lora.tabletos.ui.layout.VerticalLayout;
import com.lora.tabletos.ui.widgets.IWidget;
import com.lora.tabletos.ui.widgets.ScrollPane;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.lwjgl.glfw.GLFW;

// Импорты для Lua
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TerminalApp implements IApplication {

    private IApplicationApi api;

    // UI Components
    private ScrollPane scrollPane;
    private VerticalLayout outputLayout;

    private String currentInput = "";
    private String currentPath = "/";
    private boolean showCursor = true;
    private int cursorTick = 0;
    private boolean isInputLocked = false;

    // Lua & Commands
    private final Map<String, Consumer<String[]>> javaCommands = new ConcurrentHashMap<>();
    private final Map<String, LuaValue> luaCommands = new ConcurrentHashMap<>();
    private Globals luaGlobals;

    // Colors
    private static final int BG_COLOR = 0xFF121212;         // Очень темный фон
    private static final int INPUT_BG_COLOR = 0xFF1E1E1E;   // Чуть светлее для ввода
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int PROMPT_COLOR = 0xFF4CAF50;     // Зеленый
    private static final int ERROR_COLOR = 0xFFFF5555;      // Красный
    private static final int COMMAND_COLOR = 0xFF81C784;    // Светло-зеленый для ввода пользователя

    // Layout constants
    private static final int DOCK_RESERVED_HEIGHT = 60; // Место под док-панель снизу
    private static final int INPUT_HEIGHT = 26;         // Высота поля ввода
    private int inputY;                                 // Вычисляемая Y координата поля ввода

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;

        int[] size = api.getScreenSize();
        int w = size[0];
        int h = size[1];

        // Рассчитываем геометрию
        // Поле ввода находится над Доком
        this.inputY = h - DOCK_RESERVED_HEIGHT - INPUT_HEIGHT;

        // Скролл занимает все место от верха до поля ввода
        // Добавляем небольшой отступ сверху (topMargin), чтобы текст не прилипал к статус-бару
        int topMargin = 5;
        this.scrollPane = new ScrollPane(0, topMargin, w, inputY - topMargin);

        this.outputLayout = new VerticalLayout(0, 0, w - 14); // -14 под скроллбар
        // Padding внутри лэйаута (отступ текста от краев скролла)
        this.outputLayout.setPadding(15);
        this.outputLayout.setSpacing(4);

        this.scrollPane.setContent(this.outputLayout);

        // Приветствие
        println("LoraCore Terminal v2.0", PROMPT_COLOR, 2.0f);
        println("Type 'help' for a list of commands.", TEXT_COLOR, 1.0f);
        println("----------------------------------------", 0xFF444444, 1.0f);
        println("", 0, 1.0f); // Пустая строка

        registerJavaCommands();
        initializeLuaEnvironment();
        loadScriptCommands();
    }

    // Метод для вывода текста с поддержкой цвета и масштаба
    private void println(String text, int color, float scale) {
        // Анонимный класс виджета для отрисовки текста
        outputLayout.addWidget(new IWidget() {
            int x, y;
            // Высота строки зависит от масштаба (базовая 10 + отступы)
            final int height = (int)(14 * scale);

            @Override
            public void render(IKernelGraphics g, int mouseX, int mouseY) {
                g.drawString(text, x, y, color, scale);
            }

            @Override public void setPosition(int x, int y) { this.x = x; this.y = y; }
            @Override public void setSize(int w, int h) {}
            @Override public int getX() { return x; }
            @Override public int getY() { return y; }
            @Override public int getWidth() { return 0; }
            @Override public int getHeight() { return height; }
            @Override public boolean onEvent(KernelEvent event) { return false; }
        });
    }

    // Перегрузка для простого вывода
    private void println(String text) {
        println(text, TEXT_COLOR, 1.0f);
    }

    private void registerJavaCommands() {
        javaCommands.put("help", args -> {
            println("Available commands:", 0xFFAAAAAA, 1.0f);
            println("  help, clear, echo, ls, cat, cd", 0xFFFFFFFF, 1.0f);
            if (!luaCommands.isEmpty()){
                println("LUA commands: " + String.join(", ", luaCommands.keySet()), 0xFFAAAAAA, 1.0f);
            }
        });
        javaCommands.put("clear", args -> outputLayout.clear());
        javaCommands.put("echo", args -> println(String.join(" ", args)));
        javaCommands.put("ls", this::executeLs);
        javaCommands.put("cat", this::executeCat);
        javaCommands.put("cd", this::executeCd);
    }

    private void initializeLuaEnvironment() {
        luaGlobals = JsePlatform.standardGlobals();
        LuaTable termApi = new LuaTable();
        termApi.set("println", new VarArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                println(arg.tojstring());
                return NIL;
            }
        });
        luaGlobals.set("term", termApi);
    }

    private void loadScriptCommands() {
        String binPath = "/os/bin";
        api.getVfs().list(binPath).thenAccept(files -> {
            if (files == null || files.isEmpty()) return;

            for (String fileName : files) {
                if (fileName.endsWith(".lua")) {
                    String commandName = fileName.substring(0, fileName.length() - 4);
                    String filePath = binPath + "/" + fileName;

                    api.getVfs().readBytes(filePath).thenAccept(bytesOpt -> {
                        bytesOpt.ifPresent(bytes -> {
                            try {
                                String scriptContent = new String(bytes, StandardCharsets.UTF_8);
                                LuaValue chunk = luaGlobals.load(scriptContent, "@" + commandName);
                                LuaValue result = chunk.call();
                                if (result.isfunction()) {
                                    luaCommands.put(commandName, result);
                                }
                            } catch (Exception e) {
                                println("Error loading command '" + commandName + "': " + e.getMessage(), ERROR_COLOR, 1.0f);
                            }
                        });
                    });
                }
            }
        });
    }

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        int[] size = api.getScreenSize();
        int w = size[0];
        int h = size[1];

        // 1. Фон всего приложения
        g.fill(0, 0, w, h, BG_COLOR);

        // 2. История вывода (скролл)
        scrollPane.render(g, mouseX, mouseY);

        // 3. Поле ввода
        // Рисуем фон поля ввода
        g.fill(0, inputY, w, inputY + INPUT_HEIGHT, INPUT_BG_COLOR);
        // Верхняя граница поля ввода
        g.fill(0, inputY, w, inputY + 1, 0xFF333333);
        // Нижняя граница (отделяет от зоны дока)
        g.fill(0, inputY + INPUT_HEIGHT, w, inputY + INPUT_HEIGHT + 1, 0xFF000000);

        // Курсор мигание
        cursorTick++;
        if (cursorTick > 30) { showCursor = !showCursor; cursorTick = 0; }

        String prompt = currentPath + "> ";
        int promptWidth = g.getStringWidth(prompt);
        int scaledPromptWidth = (int)(promptWidth * 1.5f);

        int textCenterY = inputY + (INPUT_HEIGHT - 12) / 2; // Центрирование текста по вертикали

        // Рисуем промпт
        g.drawString(prompt, 10, textCenterY, PROMPT_COLOR, 1.5f);

        // Рисуем текущий ввод
        g.drawString(currentInput, 10 + scaledPromptWidth, textCenterY, TEXT_COLOR, 1.5f);

        // Рисуем каретку (курсор)
        if (showCursor && !isInputLocked) {
            int inputWidth = (int)(g.getStringWidth(currentInput) * 1.5f);
            int cursorX = 10 + scaledPromptWidth + inputWidth;
            g.fill(cursorX, textCenterY - 2, cursorX + 10, textCenterY + 14, TEXT_COLOR);
        }
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        // Скроллинг
        if (scrollPane.onEvent(event)) return true;

        if (isInputLocked) return false;

        if (event instanceof KernelEvent.CharTyped e) {
            currentInput += e.chr;
            return true;
        }

        if (event instanceof KernelEvent.KeyPressed e) {
            if (e.keyCode == GLFW.GLFW_KEY_ENTER) {
                processCommand();
                return true;
            }
            if (e.keyCode == GLFW.GLFW_KEY_BACKSPACE && !currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
                return true;
            }
        }
        return false;
    }

    private void processCommand() {
        String cmd = currentInput.trim();
        // Выводим команду пользователя в историю с особым цветом
        println(currentPath + "> " + cmd, COMMAND_COLOR, 1.0f);
        currentInput = "";

        if (cmd.isEmpty()) return;

        String[] parts = cmd.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (javaCommands.containsKey(commandName)) {
            javaCommands.get(commandName).accept(args);
        } else if (luaCommands.containsKey(commandName)) {
            executeLuaCommand(commandName, args);
        } else {
            println("Unknown command: " + commandName, ERROR_COLOR, 1.0f);
        }
    }

    private void executeLuaCommand(String commandName, String[] args) {
        LuaValue commandFunc = luaCommands.get(commandName);
        LuaTable argsTable = new LuaTable();
        for (int i = 0; i < args.length; i++) {
            argsTable.set(i + 1, LuaValue.valueOf(args[i]));
        }

        try {
            commandFunc.call(argsTable);
        } catch (Exception e) {
            println("Error executing '" + commandName + "': " + e.getMessage(), ERROR_COLOR, 1.0f);
        }
    }

    private void executeLs(String[] args) {
        isInputLocked = true;
        String pathToList = args.length > 0 ? args[0] : currentPath;

        api.getVfs().list(pathToList).whenComplete((files, error) -> {
            api.runOnRenderThread(() -> {
                if (error != null) {
                    println("Error: " + error.getMessage(), ERROR_COLOR, 1.0f);
                } else if (files.isEmpty()) {
                    println("Directory is empty.", 0xFFAAAAAA, 1.0f);
                } else {
                    println("Contents of " + pathToList + ":", 0xFFAAAAAA, 1.0f);
                    files.forEach(file -> println("  " + file, TEXT_COLOR, 1.0f));
                }
                isInputLocked = false;
            });
        });
    }

    private void executeCat(String[] args) {
        if (args.length == 0) {
            println("Usage: cat [file_path]", 0xFFAAAAAA, 1.0f);
            return;
        }
        isInputLocked = true;
        String filePath = args[0];

        api.getVfs().readBytes(filePath).whenComplete((bytesOpt, error) -> {
            api.runOnRenderThread(() -> {
                if (error != null) {
                    println("Error: " + error.getMessage(), ERROR_COLOR, 1.0f);
                } else if (bytesOpt.isEmpty()) {
                    println("Error: File not found or is directory.", ERROR_COLOR, 1.0f);
                } else {
                    try {
                        String content = new String(bytesOpt.get(), StandardCharsets.UTF_8);
                        Arrays.stream(content.split("\n")).forEach(line -> println(line));
                    } catch (Exception e) {
                        println("Error decoding file.", ERROR_COLOR, 1.0f);
                    }
                }
                isInputLocked = false;
            });
        });
    }

    private void executeCd(String[] args) {
        if (args.length == 0) {
            println("Usage: cd [path]", 0xFFAAAAAA, 1.0f);
            return;
        }
        String newPath = args[0];
        if (!newPath.endsWith("/")) newPath += "/";
        // Простая реализация без проверки существования (для скорости)
        // В идеале нужно делать exists() перед сменой
        currentPath = newPath;
    }

    @Override public void onClose() {}
    @Override public void onResume() {}
    @Override public void onPause() {}
}