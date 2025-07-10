package com.loracore.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityServerMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void onServerInteractMob(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        // Этот Mixin работает на ОБЕИХ сторонах, поэтому мы проверяем, что мы на СЕРВЕРЕ.
        if (!player.getWorld().isClient() && player.isSneaking()) {
            // Если игрок зажал Shift, просто отменяем взаимодействие на сервере.
            // Это не позволит серверу отправить пакет на открытие меню торговли.
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}