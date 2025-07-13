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
    // Карта: UUID игрока -> уровень дружбы
    private final Map<UUID, Integer> friendshipLevels = new ConcurrentHashMap<>();

    @Override
    public boolean hasGeneratedData() {
        return this.hasGeneratedData;
    }

    @Override
    public void setHasGeneratedData(boolean hasGenerated) {
        this.hasGeneratedData = hasGenerated;
    }

    @Override
    public String getVillagerName() {
        return this.villagerName;
    }

    @Override
    public void setVillagerName(String name) {
        this.villagerName = name;
    }

    @Override
    public String getPersonality() {
        return this.personality;
    }

    @Override
    public void setPersonality(String personality) {
        this.personality = personality;
    }

    @Override
    public boolean hasQuestForPlayer(UUID playerUuid) {
        return this.assignedQuests.containsKey(playerUuid);
    }

    @Override
    public void assignQuestToPlayer(UUID playerUuid, Quest quest) {
        this.assignedQuests.put(playerUuid, quest);
    }

    @Override
    public void completeQuestForPlayer(UUID playerUuid) {
        this.assignedQuests.remove(playerUuid);
    }

    @Override
    public Quest getAssignedQuest(UUID playerUuid) {
        return this.assignedQuests.get(playerUuid);
    }

    @Override
    public int getFriendship(UUID playerUuid) {
        return this.friendshipLevels.getOrDefault(playerUuid, 0);
    }

    @Override
    public void setFriendship(UUID playerUuid, int level) {
        this.friendshipLevels.put(playerUuid, level);
    }

    @Override
    public void addFriendship(UUID playerUuid, int amount) {
        int currentFriendship = getFriendship(playerUuid);
        setFriendship(playerUuid, currentFriendship + amount);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        this.hasGeneratedData = tag.getBoolean("HasGeneratedData");
        this.villagerName = tag.getString("VillagerName");
        this.personality = tag.getString("Personality");

        // Чтение квестов
        assignedQuests.clear();
        if (tag.contains("AssignedQuests", NbtElement.LIST_TYPE)) {
            NbtList questsList = tag.getList("AssignedQuests", NbtElement.COMPOUND_TYPE);
            for (NbtElement element : questsList) {
                NbtCompound questTag = (NbtCompound) element;
                UUID playerUuid = questTag.getUuid("PlayerUUID");
                Quest quest = Quest.fromNbt(questTag.getCompound("QuestData"));
                assignedQuests.put(playerUuid, quest);
            }
        }

        // Чтение данных о дружбе
        friendshipLevels.clear();
        if (tag.contains("FriendshipLevels", NbtElement.LIST_TYPE)) {
            NbtList friendshipList = tag.getList("FriendshipLevels", NbtElement.COMPOUND_TYPE);
            for (NbtElement element : friendshipList) {
                NbtCompound friendshipTag = (NbtCompound) element;
                UUID playerUuid = friendshipTag.getUuid("PlayerUUID");
                int level = friendshipTag.getInt("Level");
                friendshipLevels.put(playerUuid, level);
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("HasGeneratedData", this.hasGeneratedData);
        tag.putString("VillagerName", this.villagerName);
        tag.putString("Personality", this.personality);

        // Запись квестов с проверкой на null
        if (assignedQuests != null && !assignedQuests.isEmpty()) {
            NbtList questsList = new NbtList();
            for (Map.Entry<UUID, Quest> entry : assignedQuests.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    NbtCompound questTag = new NbtCompound();
                    questTag.putUuid("PlayerUUID", entry.getKey());
                    questTag.put("QuestData", entry.getValue().writeNbt());
                    questsList.add(questTag);
                }
            }
            tag.put("AssignedQuests", questsList);
        }

        // Запись данных о дружбе с проверкой на null
        if (friendshipLevels != null && !friendshipLevels.isEmpty()) {
            NbtList friendshipList = new NbtList();
            for (Map.Entry<UUID, Integer> entry : friendshipLevels.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    NbtCompound friendshipTag = new NbtCompound();
                    friendshipTag.putUuid("PlayerUUID", entry.getKey());
                    friendshipTag.putInt("Level", entry.getValue());
                    friendshipList.add(friendshipTag);
                }
            }
            tag.put("FriendshipLevels", friendshipList);
        }
    }
}