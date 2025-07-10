package com.aiassist.mixin.client;

import com.aiassist.component.ModComponents;
import com.aiassist.component.VillagerDataComponent;
import com.aiassist.gui.VillagerDialogueScreen;
import com.aiassist.network.RequestVillagerDataC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {

    // ВАЖНО: Добавлен параметр cancellable = true, чтобы отмена работала
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {

        // Проверяем, что код выполняется на клиенте, игрок зажимает Shift
        // и взаимодействие происходит основной рукой (во избежание двойного срабатывания)
        if (player.getWorld().isClient() && player.isSneaking() && hand == Hand.MAIN_HAND) {

            VillagerEntity thisVillager = (VillagerEntity) (Object) this;
            VillagerDataComponent component = ModComponents.VILLAGER_DATA.get(thisVillager);

            if (component.hasGeneratedData()) {
                // Если данные о жителе уже сгенерированы, открываем экран диалога
                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(new VillagerDialogueScreen(thisVillager))
                );
            } else {
                // Если данных нет, отправляем пакет на сервер для их генерации
                ClientPlayNetworking.send(new RequestVillagerDataC2SPacket(thisVillager.getUuid()));
                player.sendMessage(Text.literal("§eОтправлен запрос на знакомство..."), true);
            }

            // Отменяем стандартное действие (открытие меню торговли)
            // Это будет работать только потому, что в @Inject указано cancellable = true
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}