package com.aiassist.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

public class VillagerDataComponentImpl implements VillagerDataComponent {

    private boolean hasGeneratedData = false;
    private String villagerName = "";
    private String personality = "";

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
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        this.hasGeneratedData = tag.getBoolean("HasGeneratedData");
        this.villagerName = tag.getString("VillagerName");
        this.personality = tag.getString("Personality");
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("HasGeneratedData", this.hasGeneratedData);
        tag.putString("VillagerName", this.villagerName);
        tag.putString("Personality", this.personality);
    }
}