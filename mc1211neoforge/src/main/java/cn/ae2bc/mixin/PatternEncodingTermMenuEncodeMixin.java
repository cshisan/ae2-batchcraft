package cn.ae2bc.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import cn.ae2bc.pattern.MaterialOutputEncodingContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PatternEncodingTermMenu.class, priority = 500)
public abstract class PatternEncodingTermMenuEncodeMixin {
    @Shadow
    @Final
    private PatternEncodingLogic encodingLogic;

    @WrapMethod(method = "encode")
    private void ae2bc$withMaterialOutputEncodingContext(Operation<Void> original) {
        PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
        if (menu.isClientSide() || menu.getMode() != EncodingMode.PROCESSING) {
            original.call();
            return;
        }

        try (var ignored = MaterialOutputEncodingContext.enter(encodingLogic)) {
            original.call();
        }
    }
}
