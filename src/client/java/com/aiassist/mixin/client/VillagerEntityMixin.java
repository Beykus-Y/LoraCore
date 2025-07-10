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
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {

        if (player.getWorld().isClient() && player.isSneaking() && hand == Hand.MAIN_HAND) {

            VillagerEntity thisVillager = (VillagerEntity) (Object) this;
            VillagerDataComponent component = ModComponents.VILLAGER_DATA.get(thisVillager);

            if (component.hasGeneratedData()) {
                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().setScreen(new VillagerDialogueScreen(thisVillager))
                );
            } else {
                // ИЗМЕНЕНИЕ: Получаем код языка и добавляем его в конструктор пакета
                String langCode = MinecraftClient.getInstance().getLanguageManager().getLanguage();
                ClientPlayNetworking.send(new RequestVillagerDataC2SPacket(thisVillager.getUuid(), langCode));

                player.sendMessage(Text.translatable("chat.aiassist.villager.request_sent").formatted(Formatting.YELLOW), true);
            }

            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}