package com.loracore.component;

import com.loracore.quest.Quest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;

public class PlayerQuestComponentImpl implements PlayerQuestComponent {
    private final List<Quest> activeQuests = new ArrayList<>();

    @Override
    public List<Quest> getQuests() {
        return activeQuests;
    }

    @Override
    public void addQuest(Quest quest) {
        this.activeQuests.add(quest);
    }

    @Override
    public void removeQuest(int index) {
        if (index >= 0 && index < this.activeQuests.size()) {
            this.activeQuests.remove(index);
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        activeQuests.clear();
        NbtList questList = tag.getList("ActiveQuests", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : questList) {
            activeQuests.add(Quest.fromNbt((NbtCompound) element));
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList questList = new NbtList();
        for (Quest quest : activeQuests) {
            questList.add(quest.writeNbt());
        }
        tag.put("ActiveQuests", questList);
    }
}