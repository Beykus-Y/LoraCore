package com.aiassist;


import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Когда Mod Menu просит экран настроек, мы говорим ему вызвать наш метод
        return parent -> ModConfigScreen.create(parent);
    }
}