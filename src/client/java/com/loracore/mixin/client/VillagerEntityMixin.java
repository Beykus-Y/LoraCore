package com.loracore.mixin.client;

import com.loracore.component.ModComponents;
import com.loracore.component.VillagerDataComponent;
import com.loracore.gui.VillagerDialogueScreen;
import com.loracore.network.RequestVillagerDataC2SPacket;
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

                player.sendMessage(Text.translatable("chat.loracore.villager.request_sent").formatted(Formatting.YELLOW), true);
            }

            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}