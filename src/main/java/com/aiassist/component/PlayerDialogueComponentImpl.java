package com.aiassist.component;

import com.aiassist.api.dto.OpenAiApiDto.Message;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDialogueComponentImpl implements PlayerDialogueComponent {
    // Карта: UUID жителя -> его история диалога с этим игроком
    private final Map<UUID, List<Message>> dialogueHistories = new ConcurrentHashMap<>();

    @Override
    public List<Message> getDialogueHistory(UUID villagerUuid) {
        return dialogueHistories.computeIfAbsent(villagerUuid, k -> new ArrayList<>());
    }

    @Override
    public void addMessageToHistory(UUID villagerUuid, Message message) {
        getDialogueHistory(villagerUuid).add(message);
    }

    @Override
    public void initializeDialogue(UUID villagerUuid, String systemPrompt) {
        List<Message> history = getDialogueHistory(villagerUuid);
        // Если история пуста, добавляем системный промпт
        if (history.isEmpty()) {
            history.add(new Message("system", systemPrompt));
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        dialogueHistories.clear();
        NbtCompound historiesTag = tag.getCompound("DialogueHistories");
        for (String villagerUuidStr : historiesTag.getKeys()) {
            UUID villagerUuid = UUID.fromString(villagerUuidStr);
            NbtList historyList = historiesTag.getList(villagerUuidStr, NbtElement.COMPOUND_TYPE);
            List<Message> history = new ArrayList<>();
            for (NbtElement element : historyList) {
                NbtCompound msgTag = (NbtCompound) element;
                history.add(new Message(msgTag.getString("role"), msgTag.getString("content")));
            }
            dialogueHistories.put(villagerUuid, history);
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound historiesTag = new NbtCompound();
        for (Map.Entry<UUID, List<Message>> entry : dialogueHistories.entrySet()) {
            NbtList historyList = new NbtList();
            for (Message msg : entry.getValue()) {
                NbtCompound msgTag = new NbtCompound();
                msgTag.putString("role", msg.role());
                msgTag.putString("content", msg.content());
                historyList.add(msgTag);
            }
            historiesTag.put(entry.getKey().toString(), historyList);
        }
        tag.put("DialogueHistories", historiesTag);
    }
}