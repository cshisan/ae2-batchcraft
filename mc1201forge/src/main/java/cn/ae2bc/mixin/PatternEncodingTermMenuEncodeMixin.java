package cn.ae2bc.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.menu.slot.RestrictedInputSlot;
import cn.ae2bc.extension.PatternEncodingLogicExtension;
import cn.ae2bc.platform.ItemData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Writes output routing onto the encoded stack at AE2's final slot insertion boundary. */
@Mixin(value = PatternEncodingTermMenu.class, priority = 500)
public abstract class PatternEncodingTermMenuEncodeMixin {
    @Shadow @Final private PatternEncodingLogic encodingLogic;
    @Shadow public abstract EncodingMode getMode();

    @ModifyArg(method = "encode", at = @At(value = "INVOKE",
            target = "Lappeng/menu/slot/RestrictedInputSlot;set(Lnet/minecraft/world/item/ItemStack;)V",
            ordinal = 1), index = 0)
    private ItemStack ae2bc$writeMaterialOutputConfig(ItemStack encodedPattern) {
        if (getMode() == EncodingMode.PROCESSING) {
            ItemData.setMaterialOutput(encodedPattern,
                    ((PatternEncodingLogicExtension) encodingLogic).ae2bc$getMaterialOutputConfig());
        }
        return encodedPattern;
    }
}
