package com.loracore.component;

import com.loracore.api.dto.OpenAiApiDto.Message;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerAskHistoryComponentImpl implements PlayerAskHistoryComponent {
    private final List<Message> history = new ArrayList<>();

    @Override
    public List<Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void addMessage(Message message) {
        this.history.add(message);
    }

    @Override
    public void clearHistory() {
        this.history.clear();
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        history.clear();
        NbtList historyList = tag.getList("History", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : historyList) {
            NbtCompound msgTag = (NbtCompound) element;
            history.add(new Message(msgTag.getString("role"), msgTag.getString("content")));
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList historyList = new NbtList();
        for (Message msg : this.history) {
            NbtCompound msgTag = new NbtCompound();
            msgTag.putString("role", msg.role());
            msgTag.putString("content", msg.content());
            historyList.add(msgTag);
        }
        tag.put("History", historyList);
    }
}