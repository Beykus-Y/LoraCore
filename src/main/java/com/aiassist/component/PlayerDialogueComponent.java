package com.aiassist.component;

import com.aiassist.api.dto.OpenAiApiDto.Message;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.List;
import java.util.UUID;

public interface PlayerDialogueComponent extends Component, AutoSyncedComponent {
    /**
     * Возвращает историю диалога с конкретным жителем.
     * Если истории нет, создает и возвращает новую пустую.
     */
    List<Message> getDialogueHistory(UUID villagerUuid);

    /**
     * Добавляет сообщение в историю диалога с конкретным жителем.
     */
    void addMessageToHistory(UUID villagerUuid, Message message);

    /**
     * Устанавливает начальный системный промпт для нового диалога.
     */
    void initializeDialogue(UUID villagerUuid, String systemPrompt);
}