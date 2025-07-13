package com.loracore.component;


import com.loracore.quest.Quest;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.UUID;

public interface VillagerDataComponent extends Component, AutoSyncedComponent {

    /**
     * Проверяет, была ли уже сгенерирована личность для этого жителя.
     */
    boolean hasGeneratedData();

    /**
     * Устанавливает, что данные были сгенерированы.
     */
    void setHasGeneratedData(boolean hasGenerated);

    /**
     * Получает сгенерированное имя жителя.
     * @return Имя жителя или пустая строка, если не сгенерировано.
     */
    String getVillagerName();

    /**
     * Устанавливает имя жителя.
     */
    void setVillagerName(String name);

    /**
     * Получает сгенерированную личность/характер жителя.
     * @return Описание личности или пустая строка.
     */
    String getPersonality();

    /**
     * Устанавливает личность жителя.
     */
    void setPersonality(String personality);
    boolean hasQuestForPlayer(UUID playerUuid);
    void assignQuestToPlayer(UUID playerUuid, Quest quest);
    void completeQuestForPlayer(UUID playerUuid);
    Quest getAssignedQuest(UUID playerUuid);
    /**
     * Получает уровень дружбы жителя с указанным игроком.
     * @param playerUuid UUID игрока.
     * @return Уровень дружбы (по умолчанию 0).
     */
    int getFriendship(UUID playerUuid);

    /**
     * Устанавливает уровень дружбы для указанного игрока.
     * @param playerUuid UUID игрока.
     * @param level Новый уровень дружбы.
     */
    void setFriendship(UUID playerUuid, int level);

    /**
     * Добавляет указанное количество очков к дружбе с игроком.
     * @param playerUuid UUID игрока.
     * @param amount Количество очков для добавления (может быть отрицательным).
     */
    void addFriendship(UUID playerUuid, int amount);

}