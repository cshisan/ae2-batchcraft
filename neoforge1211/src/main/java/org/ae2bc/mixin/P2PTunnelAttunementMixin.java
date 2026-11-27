package org.ae2bc.mixin;

import appeng.api.features.P2PTunnelAttunement;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.placer.ComponentPlacerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(P2PTunnelAttunement.class)
public abstract class P2PTunnelAttunementMixin {
    /* 放置器的 FE 充电能力不能抢占内存卡读写，其他物品仍按 AE2 规则转换通道。 */
    @Inject(method = "getTunnelPartByTriggerItem", at = @At("HEAD"), cancellable = true)
    private static void ae2bc$keepPlacerMemoryCardInteraction(
            ItemStack trigger, CallbackInfoReturnable<ItemStack> cir
    ) {
        if (trigger.getItem() instanceof ComponentPlacerItem) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
