package com.aiassist.quest;

import com.aiassist.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

// Основной record для квеста
public record Quest(
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

        return new Quest(title, description, goal, reward);
    }

    // Статический метод для создания квеста-заглушки для теста
    public static Quest createDebugQuest() {
        return new Quest(
                "Просьба фермера",
                "Мне нужно 10 морковок для моего рагу. Поможешь?",
                new FetchGoal(Items.CARROT, 10),
                new QuestReward(ModItems.EMERALD_SHARD, 5)
        );
    }
}