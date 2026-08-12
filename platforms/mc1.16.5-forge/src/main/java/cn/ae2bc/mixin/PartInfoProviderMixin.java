package cn.ae2bc.mixin;

import java.util.List;

import cn.ae2bc.integration.PatternP2PTooltipProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends this mod's provider to AE2's optional TOP part provider chain. */
@Pseudo
@Mixin(targets = "appeng.integration.modules.theoneprobe.PartInfoProvider", remap = false)
public abstract class PartInfoProviderMixin {
    @Shadow @Final private List<Object> providers;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2bc$appendProvider(CallbackInfo callback) {
        PatternP2PTooltipProvider.appendTopProvider(providers);
    }
}
