// [НОВЫЙ ФАЙЛ]
package com.loracore.computer.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для пометки Java-методов, которые должны быть доступны из Lua.
 * Позволяет автоматически генерировать API для виртуальных устройств.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Callback {
    /**
     * Явное имя функции в Lua. Если не указано, используется имя Java-метода.
     * @return Имя функции в Lua.
     */
    String value() default "";

    /**
     * Описание функции для автоматической генерации документации.
     * @return Описание.
     */
    String doc() default "";
}