// Новый файл: src/main/java/com/loracore/computer/TabletScreenManager.java
package com.loracore.computer;

import com.loracore.component.ModComponents;
import com.loracore.item.TabletItem;
import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный менеджер, который отслеживает состояние экрана для каждого
 * уникального экземпляра планшета. Работает как синглтон.
 */
public class TabletScreenManager {

    private static final TabletScreenManager INSTANCE = new TabletScreenManager();
    private final Map<UUID, ServerScreenState> screenStates = new ConcurrentHashMap<>();

    private TabletScreenManager() {}

    public static TabletScreenManager getInstance() {
        return INSTANCE;
    }

    /**
     * Получает или создает состояние экрана для данного ItemStack'а планшета.
     * Если у планшета еще нет уникального UUID, он будет сгенерирован и сохранен в предмете.
     * @param tabletStack ItemStack планшета.
     * @return Экземпляр ServerScreenState, связанный с этим планшетом.
     */
    public ServerScreenState getOrCreateScreen(ItemStack tabletStack) {
        // Проверяем, что это действительно наш планшет
        if (!(tabletStack.getItem() instanceof TabletItem)) {
            throw new IllegalArgumentException("ItemStack is not a LoraCore Tablet!");
        }

        // ИСПРАВЛЕНИЕ: Синхронизируем доступ к UUID для предотвращения race condition
        synchronized (tabletStack) {
            // Пытаемся получить UUID из Data Component предмета.
            UUID tabletUuid = tabletStack.get(ModComponents.TABLET_UUID);

            // Если UUID отсутствует (например, планшет только что скрафчен),
            // генерируем новый и записываем его в предмет.
            if (tabletUuid == null) {
                tabletUuid = UUID.randomUUID();
                tabletStack.set(ModComponents.TABLET_UUID, tabletUuid);
            }

            // Используем этот UUID, чтобы найти или создать состояние экрана в нашей карте.
            // computeIfAbsent гарантирует, что для одного UUID будет создан только один экземпляр ServerScreenState.
            return screenStates.computeIfAbsent(tabletUuid, uuid -> new ServerScreenState());
        }
    }

    /**
     * Получает состояние экрана по UUID, если оно уже существует.
     * @param tabletUuid UUID планшета.
     * @return Optional, содержащий ServerScreenState, или пустой, если состояние не найдено.
     */
    public ServerScreenState getScreen(UUID tabletUuid) {
        return screenStates.get(tabletUuid);
    }
}