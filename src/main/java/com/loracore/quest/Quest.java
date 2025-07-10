package com.loracore.quest;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.UUID; // Добавлен импорт

// ИЗМЕНЕНИЕ 1: Добавлены поля questId и villagerGiverUuid
public record Quest(
        UUID questId,
        UUID villagerGiverUuid,
        String title,
        String description,
        FetchGoal goal,
        QuestReward reward
) {
    // Вложенный record для цели "Принеси предмет"
    public record FetchGoal(Item item, int requiredAmount) { }

    // Вложенный record для награды
    public record QuestReward(Item item, int amount) { }

    // Метод для сохранения квеста в NBT
    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        // ИЗМЕНЕНИЕ 2: Сохранение новых полей UUID
        nbt.putUuid("questId", questId);
        nbt.putUuid("villagerGiverUuid", villagerGiverUuid);
        nbt.putString("title", title);
        nbt.putString("description", description);

        NbtCompound goalNbt = new NbtCompound();
        goalNbt.putString("item", Registries.ITEM.getId(goal.item()).toString());
        goalNbt.putInt("requiredAmount", goal.requiredAmount());
        nbt.put("goal", goalNbt);

        NbtCompound rewardNbt = new NbtCompound();
        rewardNbt.putString("item", Registries.ITEM.getId(reward.item()).toString());
        rewardNbt.putInt("amount", reward.amount());
        nbt.put("reward", rewardNbt);

        return nbt;
    }

    // Статический метод для загрузки квеста из NBT
    public static Quest fromNbt(NbtCompound nbt) {
        // ИЗМЕНЕНИЕ 3: Загрузка новых полей UUID
        UUID questId = nbt.getUuid("questId");
        UUID villagerGiverUuid = nbt.getUuid("villagerGiverUuid");
        String title = nbt.getString("title");
        String description = nbt.getString("description");

        NbtCompound goalNbt = nbt.getCompound("goal");
        Item goalItem = Registries.ITEM.get(new Identifier(goalNbt.getString("item")));
        int requiredAmount = goalNbt.getInt("requiredAmount");
        FetchGoal goal = new FetchGoal(goalItem, requiredAmount);

        NbtCompound rewardNbt = nbt.getCompound("reward");
        Item rewardItem = Registries.ITEM.get(new Identifier(rewardNbt.getString("item")));
        int rewardAmount = rewardNbt.getInt("amount");
        QuestReward reward = new QuestReward(rewardItem, rewardAmount);

        return new Quest(questId, villagerGiverUuid, title, description, goal, reward);
    }

    // ИЗМЕНЕНИЕ 4: Метод-заглушка удален, так как квесты теперь генерируются динамически
    /*
    public static Quest createDebugQuest() {
        // Этот код больше не нужен
    }
    */
}