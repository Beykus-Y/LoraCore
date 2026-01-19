package com.loracore.lang.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class CompilerContext {
    public final CodeEmitter emitter = new CodeEmitter();

    private final Map<String, String> constants = new HashMap<>();
    private final Map<String, String> variables = new HashMap<>();
    private final Stack<String> loopEndStack = new Stack<>();
    private final Map<String, String> locals = new HashMap<>();
    private final Map<String, String> globals = new HashMap<>();
    private String currentFunctionExitLabel = null;
    private final Map<String, String> stringLiterals = new HashMap<>(); // "text" -> "str_1"
    private int stringCounter = 0;


    private int labelCounter = 0;
    private int varCounter = 0;

    public void setCurrentFunctionExit(String label) {
        this.currentFunctionExitLabel = label;
    }

    public String getCurrentFunctionExit() {
        return currentFunctionExitLabel;
    }

    public void addConstant(String name, String value) {
        constants.put(name, value);
    }

    public String getConstant(String name) {
        return constants.get(name);
    }
    public void registerVariable(String name, String label) {
        locals.put(name, label);
    }

    public boolean hasConstant(String name) {
        return constants.containsKey(name);
    }

    public String declareVariable(String name) {
        String label = "v_" + name + "_" + (++varCounter);
        locals.put(name, label);
        emitter.emitData(label, 0);
        return label;
    }
    public String declareGlobal(String name, int initialValue) {
        String label = "g_" + name + "_" + (++varCounter);
        globals.put(name, label);
        emitter.emitData(label, initialValue);
        return label;
    }

    public String getVariableLabel(String name) {
        if (locals.containsKey(name)) return locals.get(name);
        return globals.get(name);
    }
    public String registerStringLiteral(String content) {
        if (content == null) content = "";

        // Дедупликация
        if (stringLiterals.containsKey(content)) {
            return stringLiterals.get(content);
        }

        String label = "str_" + (++stringCounter);
        stringLiterals.put(content, label);

        // ВАЖНОЕ ИЗМЕНЕНИЕ:
        // Раньше тут было emitter.emitLabel(label); -> это ставило метку в поток КОДА.
        // Мы убрали это. Теперь метка передается в emitData первого символа.

        char[] chars = content.toCharArray();

        if (chars.length > 0) {
            // Первый символ эмитим С МЕТКОЙ
            emitter.emitData(label, (int) chars[0]);

            // Остальные без метки
            for (int i = 1; i < chars.length; i++) {
                emitter.emitData(null, (int) chars[i]);
            }
        } else {
            // Если строка пустая "", просто эмитим 0 с меткой
            emitter.emitData(label, 0);
            return label; // Выходим, null-терминатор уже записан
        }

        // Null-terminator (конец строки)
        emitter.emitData(null, 0);

        return label;
    }

    public boolean hasVariable(String name) {
        return locals.containsKey(name) || globals.containsKey(name);
    }

    // Очистка ТОЛЬКО локальных переменных (вызывается при входе в функцию)
    public void clearVariables() {
        locals.clear();
    }

    public String nextLabel(String prefix) {
        return prefix + "_" + (++labelCounter);
    }

    public String currentLabelIndex() {
        return String.valueOf(labelCounter);
    }

    public void pushLoopEnd(String label) {
        loopEndStack.push(label);
    }

    public String popLoopEnd() {
        return loopEndStack.pop();
    }

    public String peekLoopEnd() {
        return loopEndStack.isEmpty() ? null : loopEndStack.peek();
    }
}