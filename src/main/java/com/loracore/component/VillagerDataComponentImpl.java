package com.loracore.component;

import com.loracore.quest.Quest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VillagerDataComponentImpl implements VillagerDataComponent {

    private boolean hasGeneratedData = false;
    private String villagerName = "";
    private String personality = "";

    // Карта: UUID игрока -> выданный ему квест
    private final Map<UUID, Quest> assignedQuests = new ConcurrentHashMap<>();

    @Override
    public boolean hasGeneratedData() {
        return this.hasGeneratedData;
    }

    @Override
    public void setHasGeneratedData(boolean hasGenerated) {
        this.hasGeneratedData = hasGenerated;
        // this.markDirty(); // УДАЛЕНО: Этот вызов здесь не нужен
    }

    @Override
    public String getVillagerName() {
        return this.villagerName;
    }

    @Override
    public void setVillagerName(String name) {
        this.villagerName = name;
        // this.markDirty(); // УДАЛЕНО: Этот вызов здесь не нужен
    }

    @Override
    public String getPersonality() {
        return this.personality;
    }

    @Override
    public void setPersonality(String personality) {
        this.personality = personality;
        // this.markDirty(); // УДАЛЕНО: Этот вызов здесь не нужен
    }

    @Override
    public boolean hasQuestForPlayer(UUID playerUuid) {
        return this.assignedQuests.containsKey(playerUuid);
    }

    @Override
    public void assignQuestToPlayer(UUID playerUuid, Quest quest) {
        this.assignedQuests.put(playerUuid, quest);
        // this.markDirty(); // УДАЛЕНО: Этот вызов здесь не нужен
    }

    @Override
    public void completeQuestForPlayer(UUID playerUuid) {
        this.assignedQuests.remove(playerUuid);
        // this.markDirty(); // УДАЛЕНО: Этот вызов здесь не нужен
    }

    @Override
    public Quest getAssignedQuest(UUID playerUuid) {
        return this.assignedQuests.get(playerUuid);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        this.hasGeneratedData = tag.getBoolean("HasGeneratedData");
        this.villagerName = tag.getString("VillagerName");
        this.personality = tag.getString("Personality");

        assignedQuests.clear();
        NbtList questsList = tag.getList("AssignedQuests", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : questsList) {
            NbtCompound questTag = (NbtCompound) element;
            UUID playerUuid = questTag.getUuid("PlayerUUID");
            Quest quest = Quest.fromNbt(questTag.getCompound("QuestData"));
            assignedQuests.put(playerUuid, quest);
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("HasGeneratedData", this.hasGeneratedData);
        tag.putString("VillagerName", this.villagerName);
        tag.putString("Personality", this.personality);

        NbtList questsList = new NbtList();
        for (Map.Entry<UUID, Quest> entry : assignedQuests.entrySet()) {
            NbtCompound questTag = new NbtCompound();
            questTag.putUuid("PlayerUUID", entry.getKey());
            questTag.put("QuestData", entry.getValue().writeNbt());
            questsList.add(questTag);
        }
        tag.put("AssignedQuests", questsList);
    }
}