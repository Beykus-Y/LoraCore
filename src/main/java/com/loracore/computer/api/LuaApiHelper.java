// Полный исправленный файл: src/main/java/com/loracore/computer/api/LuaApiHelper.java
package com.loracore.computer.api;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class LuaApiHelper {

    // ИСПРАВЛЕНИЕ 1: Метод теперь принимает Globals в качестве аргумента
    public static LuaTable createApi(final Object device) {
        LuaTable api = new LuaTable();
        Map<String, Method> methods = findCallbackMethods(device.getClass());

        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            String luaName = entry.getKey();
            Method javaMethod = entry.getValue();

            api.set(luaName, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    try {
                        // Передаем 'globals' в метод конвертации
                        Object[] javaArgs = convertLuaToJava(args, javaMethod.getParameters());
                        Object result = javaMethod.invoke(device, javaArgs);
                        return convertJavaToLua(result);
                    } catch (InvocationTargetException e) {
                        throw new LuaError(e.getTargetException());
                    } catch (Exception e) {
                        throw new LuaError("Error calling method '" + luaName + "': " + e.getMessage());
                    }
                }
            });
        }
        return api;
    }

    private static Map<String, Method> findCallbackMethods(Class<?> clazz) {
        Map<String, Method> result = new HashMap<>();
        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(Callback.class)) {
                Callback annotation = method.getAnnotation(Callback.class);
                String name = annotation.value().isEmpty() ? method.getName() : annotation.value();
                result.put(name, method);
            }
        }
        return result;
    }

    // ИСПРАВЛЕНИЕ 2: Метод теперь также принимает Globals
    private static Object[] convertLuaToJava( Varargs args, Parameter[] params) {
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> paramType = params[i].getType();


            if (i >= args.narg()) {
                result[i] = null;
            } else {
                // Преобразуем аргументы как и раньше
                if (paramType == int.class || paramType == Integer.class) result[i] = args.checkint(i + 1);
                else if (paramType == String.class) result[i] = args.checkjstring(i + 1);
                else if (paramType == boolean.class || paramType == Boolean.class) result[i] = args.checkboolean(i + 1);
                else if (paramType == double.class || paramType == Double.class) result[i] = args.checkdouble(i + 1);
                else if (paramType == Varargs.class) result[i] = args;
                else result[i] = args.checkuserdata(i + 1);
            }
        }
        return result;
    }

    // Методы convertJavaToLua и convertSingleJavaToLua остаются без изменений
    private static Varargs convertJavaToLua(Object obj) {
        if (obj == null) return LuaValue.NIL;
        if (obj instanceof Object[] arr) {
            LuaValue[] values = new LuaValue[arr.length];
            for (int i = 0; i < arr.length; i++) values[i] = convertSingleJavaToLua(arr[i]);
            return LuaValue.varargsOf(values);
        }
        if (obj instanceof int[] arr) {
            LuaValue[] values = new LuaValue[arr.length];
            for (int i = 0; i < arr.length; i++) values[i] = LuaValue.valueOf(arr[i]);
            return LuaValue.varargsOf(values);
        }
        return convertSingleJavaToLua(obj);
    }

    private static LuaValue convertSingleJavaToLua(Object obj) {
        if (obj == null) return LuaValue.NIL;
        if (obj instanceof Integer i) return LuaValue.valueOf(i);
        if (obj instanceof String s) return LuaValue.valueOf(s);
        if (obj instanceof Boolean b) return LuaValue.valueOf(b);
        if (obj instanceof Double d) return LuaValue.valueOf(d);
        if (obj instanceof LuaValue v) return v;
        return LuaValue.userdataOf(obj);
    }
}