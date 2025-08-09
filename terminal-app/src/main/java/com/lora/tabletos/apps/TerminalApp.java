// Файл: terminal-app/src/main/java/com/lora/tabletos/apps/TerminalApp.java
package com.lora.tabletos.apps;

import com.lora.tabletos.core.IApplication;
import com.lora.tabletos.core.IApplicationApi;
import com.loracore.computer.kernel.IKernelGraphics;
import com.loracore.computer.kernel.KernelEvent;
import org.lwjgl.glfw.GLFW;

// --- ИСПРАВЛЕНИЕ: Добавлены все недостающие импорты из LuaJ ---
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
// --- Конец исправления ---

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TerminalApp implements IApplication {

    private IApplicationApi api;
    private final List<String> history = new ArrayList<>();
    private String currentInput = "";
    private String currentPath = "/";
    private boolean isInputLocked = false;

    private double scrollAmount = 0;
    private int totalHistoryHeight = 0;
    private int contentAreaHeight = 0;
    private boolean isScrolledToBottom = true;

    private static final int BG_COLOR = 0xFF1E1E1E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 5;

    private int cursorTick = 0;
    private boolean showCursor = true;

    private final Map<String, Consumer<String[]>> javaCommands = new ConcurrentHashMap<>();
    private final Map<String, LuaValue> luaCommands = new ConcurrentHashMap<>();
    private Globals luaGlobals;

    @Override
    public void onLoad(IApplicationApi api) {
        this.api = api;
        println("LoraCore Terminal v1.1 | LUA scripting enabled");
        println("Type 'help' for a list of commands.");
        println("");
        registerJavaCommands();
        initializeLuaEnvironment();
        loadScriptCommands();
    }

    private void registerJavaCommands() {
        javaCommands.put("help", args -> printHelp());
        javaCommands.put("clear", args -> {
            history.clear();
            scrollToBottom();
        });
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
                                    System.out.println("Loaded Lua command: " + commandName);
                                }
                            } catch (Exception e) {
                                println("Error loading command '" + commandName + "': " + e.getMessage());
                            }
                        });
                    });
                }
            }
        });
    }

    @Override
    public void onResume() {}

    @Override
    public void onPause() {}

    @Override
    public void onRender(IKernelGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, 480, 240, BG_COLOR);
        contentAreaHeight = 240 - (PADDING * 2);

        int y = PADDING - (int)scrollAmount;
        int currentRenderedHeight = 0;

        for (String line : history) {
            if (y + LINE_HEIGHT > 0 && y < 240) {
                g.drawString(line, PADDING, y, TEXT_COLOR);
            }
            y += LINE_HEIGHT;
            currentRenderedHeight += LINE_HEIGHT;
        }
        totalHistoryHeight = currentRenderedHeight;

        String prompt = currentPath + "> ";
        if (y + LINE_HEIGHT > 0 && y < 240) {
            g.drawString(prompt + currentInput, PADDING, y, TEXT_COLOR);
        }

        cursorTick++;
        if (cursorTick > 30) {
            showCursor = !showCursor;
            cursorTick = 0;
        }
        if (showCursor && !isInputLocked && y + LINE_HEIGHT > 0 && y < 240) {
            int cursorX = PADDING + g.getStringWidth(prompt + currentInput);
            g.fill(cursorX, y, cursorX + 7, y + LINE_HEIGHT, TEXT_COLOR);
        }
    }

    @Override
    public boolean onEvent(KernelEvent event) {
        // --- ПОЛНОСТЬЮ ПЕРЕРАБОТАННЫЙ МЕТОД ---

        // Скроллинг обрабатывается в любом состоянии (даже при блокировке ввода)
        if (event instanceof KernelEvent.MouseScrolled scrollEvent) {
            int maxScroll = Math.max(0, totalHistoryHeight - contentAreaHeight);
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - scrollEvent.verticalAmount * LINE_HEIGHT));
            isScrolledToBottom = (scrollAmount >= maxScroll);
            return true; // Мы обработали это событие
        }

        // Если ввод заблокирован, игнорируем все остальные события
        if (isInputLocked) {
            return false;
        }

        if (event instanceof KernelEvent.CharTyped charEvent) {
            currentInput += charEvent.chr;
            return true; // Мы обработали это событие
        }

        if (event instanceof KernelEvent.KeyPressed keyEvent) {
            if (keyEvent.keyCode() == GLFW.GLFW_KEY_ENTER) {
                processCommand();
                return true; // Мы обработали это событие
            }
            if (keyEvent.keyCode() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!currentInput.isEmpty()) {
                    currentInput = currentInput.substring(0, currentInput.length() - 1);
                }
                return true; // Мы обработали это событие
            }
        }

        // Если ни одно из условий не сработало, значит, мы не обрабатывали это событие
        return false;
    }

    private void processCommand() {
        checkIfScrolledToBottom();
        String fullCommand = currentInput.trim();
        println(currentPath + "> " + fullCommand);
        currentInput = "";

        if (fullCommand.isEmpty()) return;

        String[] parts = fullCommand.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (javaCommands.containsKey(commandName)) {
            javaCommands.get(commandName).accept(args);
        } else if (luaCommands.containsKey(commandName)) {
            executeLuaCommand(commandName, args);
        } else {
            println("Error: Command not found '" + commandName + "'");
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
            println("Error executing '" + commandName + "': " + e.getMessage());
        }
    }

    private void printHelp() {
        println("Available commands:");
        println("  help, clear, echo, ls, cat, cd");
        if (!luaCommands.isEmpty()){
            println("LUA commands: " + String.join(", ", luaCommands.keySet()));
        }
    }

    private void executeLs(String[] args) {
        isInputLocked = true;
        String pathToList = args.length > 0 ? args[0] : currentPath;

        api.getVfs().list(pathToList).whenComplete((files, error) -> {
            if (error != null) {
                println("Error: " + error.getMessage());
            } else if (files.isEmpty()) {
                println("Directory is empty or does not exist.");
            } else {
                println("Contents of " + pathToList + ":");
                files.forEach(file -> println("  " + file));
            }
            isInputLocked = false;
        });
    }

    private void executeCat(String[] args) {
        if (args.length == 0) {
            println("Usage: cat [file_path]");
            return;
        }
        isInputLocked = true;
        String filePath = args[0];

        api.getVfs().readBytes(filePath).whenComplete((bytesOpt, error) -> {
            if (error != null) {
                println("Error: " + error.getMessage());
            } else if (bytesOpt.isEmpty()) {
                println("Error: File not found or is a directory.");
            } else {
                try {
                    String content = new String(bytesOpt.get(), StandardCharsets.UTF_8);
                    Arrays.stream(content.split("\n")).forEach(this::println);
                } catch (Exception e) {
                    println("Error: Could not decode file content.");
                }
            }
            isInputLocked = false;
        });
    }

    private void executeCd(String[] args) {
        if (args.length == 0) {
            println("Usage: cd [path]");
            return;
        }
        // TODO: Добавить валидацию пути и поддержку '..'
        currentPath = args[0];
        if (!currentPath.endsWith("/")) {
            currentPath += "/";
        }
    }

    private void println(String text) {
        checkIfScrolledToBottom();
        history.add(text);
        if (isScrolledToBottom) {
            scrollToBottom();
        }
    }

    private void checkIfScrolledToBottom() {
        isScrolledToBottom = (scrollAmount >= Math.max(0, totalHistoryHeight - contentAreaHeight));
    }

    private void scrollToBottom() {
        scrollAmount = Math.max(0, totalHistoryHeight - contentAreaHeight);
    }

    @Override
    public void onClose() {}
}