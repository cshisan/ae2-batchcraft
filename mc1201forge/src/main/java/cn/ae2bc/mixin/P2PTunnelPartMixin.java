package cn.ae2bc.mixin;

import appeng.parts.p2p.P2PTunnelPart;
import cn.ae2bc.placer.ComponentPlacerItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(P2PTunnelPart.class)
public abstract class P2PTunnelPartMixin {
    @Inject(method = "onPartActivate", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2bc$preventPlacerAttunement(Player player, InteractionHand hand, Vec3 pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (player.getItemInHand(hand).getItem() instanceof ComponentPlacerItem) {
            cir.setReturnValue(true);
        }
    }
}
