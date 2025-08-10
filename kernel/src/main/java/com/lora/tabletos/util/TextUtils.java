// Файл: kernel/src/main/java/com/lora/tabletos/util/TextUtils.java
package com.lora.tabletos.util;

import com.loracore.computer.kernel.IKernelGraphics;
import java.util.ArrayList;
import java.util.List;

/**
 * Утилитарный класс для общих операций с текстом в LoraCore OS.
 */
public final class TextUtils {

    /**
     * Приватный конструктор, чтобы предотвратить создание экземпляров.
     */
    private TextUtils() {}

    /**
     * Обрезает строку, если её ширина превышает максимальную, добавляя многоточие в конце.
     * Идеально подходит для однострочных меток, как в IconWidget.
     *
     * @param text Исходный текст.
     * @param maxWidth Максимальная ширина в пикселях.
     * @param g Графический контекст для измерения ширины строки.
     * @return Обрезанная строка с многоточием или оригинальная строка, если она помещается.
     */
    public static String ellipsize(String text, int maxWidth, IKernelGraphics g) {
        if (g.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = g.getStringWidth(ellipsis);

        // Обрезаем строку, пока она с многоточием не поместится
        while (g.getStringWidth(text + ellipsis) > maxWidth && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
        }

        return text + ellipsis;
    }

    /**
     * Разбивает длинную строку на несколько строк, каждая из которых не превышает максимальную ширину.
     * Учитывает переносы по словам.
     *
     * @param text Исходный текст.
     * @param maxWidth Максимальная ширина в пикселях.
     * @param g Графический контекст для измерения ширины.
     * @return Список строк, готовых к отрисовке.
     */
    public static List<String> wrapText(String text, int maxWidth, IKernelGraphics g) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            int wordWidth = g.getStringWidth(word);
            int currentLineWidth = g.getStringWidth(currentLine.toString());

            if (currentLineWidth + wordWidth < maxWidth) {
                currentLine.append(word).append(" ");
            } else {
                lines.add(currentLine.toString().trim());
                currentLine = new StringBuilder(word + " ");
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString().trim());
        }

        return lines;
    }
}