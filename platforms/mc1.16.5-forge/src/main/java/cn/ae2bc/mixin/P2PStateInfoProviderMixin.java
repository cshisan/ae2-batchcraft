package cn.ae2bc.mixin;

import appeng.parts.p2p.P2PTunnelPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.integration.PatternP2PTooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Includes Pattern P2P Unit managers in AE2's TOP connection-state calculation. */
@Pseudo
@Mixin(targets = "appeng.integration.modules.theoneprobe.part.P2PStateInfoProvider", remap = false)
public abstract class P2PStateInfoProviderMixin {
    @Inject(method = "getOutputCount", at = @At("RETURN"), cancellable = true, remap = false)
    private static void ae2bc$includeUnitManagers(P2PTunnelPart<?> tunnel,
            CallbackInfoReturnable<Integer> result) {
        if (tunnel instanceof PatternP2PTunnelPart) {
            result.setReturnValue(PatternP2PTooltipProvider.includeManagers(
                    result.getReturnValueI(), (PatternP2PTunnelPart) tunnel));
        }
    }
}
