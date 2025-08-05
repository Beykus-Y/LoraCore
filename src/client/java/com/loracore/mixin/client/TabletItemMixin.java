// Файл: src/client/java/com/loracore/mixin/client/TabletItemMixin.java
package com.loracore.mixin.client;

import com.loracore.item.TabletItem;
import com.loracore.network.RequestTabletDataC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Мы "примешиваемся" к нашему собственному классу TabletItem
@Mixin(TabletItem.class)
public abstract class TabletItemMixin extends Item {

    // Приватный конструктор необходим для миксинов, наследующихся от классов
    private TabletItemMixin(Settings settings) {
        super(settings);
    }

    /**
     * Этот метод-инъекция перехватывает вызов метода use() в самом его начале (at = "HEAD").
     * Он выполняется ТОЛЬКО на клиенте, так как сам миксин находится в client-части.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onUseClient(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        // Мы находимся на клиенте, поэтому эта проверка всегда будет true, но она полезна для ясности
        if (world.isClient) {
            // Отправляем наш пакет. Здесь это абсолютно безопасно.
            ClientPlayNetworking.send(new RequestTabletDataC2SPacket());

            // Мы "отменяем" оригинальный метод use(), чтобы избежать двойных действий,
            // и сразу возвращаем результат.
            cir.setReturnValue(TypedActionResult.success(player.getStackInHand(hand), true));
        }
    }
}