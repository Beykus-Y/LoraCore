package com.aiassist.component;


import com.aiassist.api.dto.OpenAiApiDto.Message;
import net.minecraft.nbt.NbtCompound;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.List;

public interface VillagerDataComponent extends Component, AutoSyncedComponent {

    /**
     * Проверяет, была ли уже сгенерирована личность для этого жителя.
     */
    boolean hasGeneratedData();

    /**
     * Устанавливает, что данные были сгенерированы.
     */
    void setHasGeneratedData(boolean hasGenerated);

    /**
     * Получает сгенерированное имя жителя.
     * @return Имя жителя или пустая строка, если не сгенерировано.
     */
    String getVillagerName();

    /**
     * Устанавливает имя жителя.
     */
    void setVillagerName(String name);

    /**
     * Получает сгенерированную личность/характер жителя.
     * @return Описание личности или пустая строка.
     */
    String getPersonality();

    /**
     * Устанавливает личность жителя.
     */
    void setPersonality(String personality);


}