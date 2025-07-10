package com.loracore.component;

import com.loracore.quest.Quest;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent; // Добавлен необходимый импорт

import java.util.List;

// ИЗМЕНЕНИЕ: Добавлено наследование от AutoSyncedComponent
public interface PlayerQuestComponent extends Component, AutoSyncedComponent {
    List<Quest> getQuests();
    void addQuest(Quest quest);
    void removeQuest(int index);
}