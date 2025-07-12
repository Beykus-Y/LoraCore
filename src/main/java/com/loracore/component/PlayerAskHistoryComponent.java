package com.loracore.component;

import com.loracore.api.dto.OpenAiApiDto.Message;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.List;

public interface PlayerAskHistoryComponent extends Component, AutoSyncedComponent {
    /**
     * Возвращает полный список сообщений в чате с AI.
     * @return Неизменяемый список сообщений.
     */
    List<Message> getHistory();

    /**
     * Добавляет сообщение в историю.
     * @param message Сообщение для добавления.
     */
    void addMessage(Message message);

    /**
     * Очищает историю чата.
     */
    void clearHistory();
}