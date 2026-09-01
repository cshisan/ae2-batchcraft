package cn.ae2bc.mixin;

import appeng.container.me.items.PatternTermContainer;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.pattern.PatternEncodingState;
import net.minecraft.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges the old AE2 pattern terminal to the canonical material-output configuration. */
@Mixin(PatternTermContainer.class)
public abstract class PatternEncodingTermMenuMixin implements PatternEncodingTermMenuExtension {
    @Unique private MaterialOutputConfigData ae2bc$materialOutputConfig = MaterialOutputConfigData.EMPTY;

    @Inject(method = "encode", at = @At("HEAD"), remap = false)
    private void ae2bc$prepareEncoding(CallbackInfo callback) {
        PatternTermContainer container = (PatternTermContainer) (Object) this;
        PatternEncodingState.clear();
        if (!container.isCraftingMode() && !ae2bc$materialOutputConfig.isEmpty()) {
            PatternEncodingState.set(ae2bc$materialOutputConfig.toPacked());
        }
    }

    @Inject(method = "encode", at = @At("RETURN"), remap = false)
    private void ae2bc$finishEncoding(CallbackInfo callback) {
        PatternEncodingState.clear();
    }

    @Override public MaterialOutputConfigData ae2bc$getMaterialOutputConfig() {
        return ae2bc$materialOutputConfig;
    }

    @Override public void ae2bc$setInputDirection(int slot, Direction direction) {
        ae2bc$materialOutputConfig = ae2bc$materialOutputConfig.withDirection(slot, direction);
    }

    @Override public void ae2bc$setMaterialOutputForm(int slot, MaterialOutputForm form) {
        ae2bc$materialOutputConfig = ae2bc$materialOutputConfig.withOutputForm(slot, form);
    }

    @Override public void ae2bc$setMaterialOutputConfig(MaterialOutputConfigData config) {
        ae2bc$materialOutputConfig = config == null ? MaterialOutputConfigData.EMPTY : config;
    }
}
