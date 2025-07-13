package com.loracore.mixin;

import com.loracore.component.ModComponents;
import com.loracore.component.VillagerDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity {

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackVillager(Entity target, CallbackInfo ci) {
        if (!getWorld().isClient() && target instanceof VillagerEntity villager) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            VillagerDataComponent data = ModComponents.VILLAGER_DATA.get(villager);

            // Понижаем дружбу на 25
            data.addFriendship(player.getUuid(), -25);
            ModComponents.VILLAGER_DATA.sync(villager);

            player.sendMessage(Text.translatable("chat.loracore.villager.hit_penalty", villager.getName(), -25).formatted(Formatting.RED), true);
        }
    }
}