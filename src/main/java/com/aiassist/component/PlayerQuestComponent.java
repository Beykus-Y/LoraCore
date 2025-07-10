package com.aiassist.component;

import com.aiassist.quest.Quest;
import org.ladysnake.cca.api.v3.component.Component;

import java.util.List;

public interface PlayerQuestComponent extends Component {
    List<Quest> getQuests();
    void addQuest(Quest quest);
    void removeQuest(int index);
}