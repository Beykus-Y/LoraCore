package com.loracore.computer.api;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

public class LuaApiHelper {

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
                        Object[] javaArgs = convertLuaToJava(args, javaMethod.getParameters());
                        Object result = javaMethod.invoke(device, javaArgs);
                        return convertJavaToLua(result);
                    } catch (InvocationTargetException e) {
                        // [УЛУЧШЕНО] Если Java-метод выбросил исключение, передаем его в Lua как ошибку.
                        // Это позволяет использовать pcall() в Lua для обработки ошибок Java.
                        throw new LuaError(e.getTargetException());
                    } catch (Exception e) {
                        // Для всех остальных ошибок (неправильные аргументы и т.д.)
                        throw new LuaError("Error calling method '" + luaName + "': " + e.getMessage());
                    }
                }
            });
        }
        return api;
    }

    // ... метод findCallbackMethods без изменений ...
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

    // ... метод convertLuaToJava без изменений ...
    private static Object[] convertLuaToJava(Varargs args, Parameter[] params) {
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (i >= args.narg()) {
                result[i] = null;
            } else {
                Class<?> type = params[i].getType();
                if (type == int.class || type == Integer.class) result[i] = args.checkint(i + 1);
                else if (type == String.class) result[i] = args.checkjstring(i + 1);
                else if (type == boolean.class || type == Boolean.class) result[i] = args.checkboolean(i + 1);
                else if (type == double.class || type == Double.class) result[i] = args.checkdouble(i + 1);
                else result[i] = args.checkuserdata(i + 1);
            }
        }
        return result;
    }

    // [УЛУЧШЕНО] Этот метод теперь поддерживает возврат нескольких значений из Java
    private static Varargs convertJavaToLua(Object obj) {
        if (obj == null) {
            return LuaValue.NIL;
        }
        // Поддержка нескольких возвращаемых значений через Object[]
        if (obj instanceof Object[] objectArray) {
            LuaValue[] values = new LuaValue[objectArray.length];
            for (int i = 0; i < objectArray.length; i++) {
                values[i] = convertSingleJavaToLua(objectArray[i]);
            }
            return LuaValue.varargsOf(values);
        }
        // Поддержка int[] (оставлена для обратной совместимости)
        if (obj instanceof int[] intArray) {
            LuaValue[] values = new LuaValue[intArray.length];
            for (int i = 0; i < intArray.length; i++) {
                values[i] = LuaValue.valueOf(intArray[i]);
            }
            return LuaValue.varargsOf(values);
        }
        // Для одиночных значений
        return convertSingleJavaToLua(obj);
    }

    // [НОВЫЙ МЕТОД] Вспомогательный метод для конвертации одного объекта
    private static LuaValue convertSingleJavaToLua(Object obj) {
        if (obj == null) return LuaValue.NIL;
        if (obj instanceof Integer i) return LuaValue.valueOf(i);
        if (obj instanceof String s) return LuaValue.valueOf(s);
        if (obj instanceof Boolean b) return LuaValue.valueOf(b);
        if (obj instanceof Double d) return LuaValue.valueOf(d);
        if (obj instanceof LuaValue l) return l; // Если Java уже вернула LuaValue
        return LuaValue.userdataOf(obj);
    }
}