package cn.ae2bc.mixin;

import appeng.api.config.ActionItems;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.widgets.ActionButton;
import appeng.menu.implementations.PatternProviderMenu;
import cn.ae2bc.extension.PatternProviderMenuExtension;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin {
    @Unique private ActionButton ae2bc$productExtractionButton;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$addProductExtractionControls(CallbackInfo ci) {
        PatternProviderScreen<?> screen = (PatternProviderScreen<?>) (Object) this;
        PatternProviderMenu menu = screen.getMenu();
        ae2bc$productExtractionButton = new ActionButton(ActionItems.COG,
                () -> ((PatternProviderMenuExtension) menu).ae2bc$openProductExtractionSettings());
        ae2bc$productExtractionButton.setMessage(Component.translatable(
                        "gui.ae2_batchcraft.product_extraction.open")
                .append("\n")
                .append(Component.translatable(
                        "gui.ae2_batchcraft.product_extraction.open.tooltip")));
        ((AEBaseScreenAccessor) screen).ae2bc$addToLeftToolbar(ae2bc$productExtractionButton);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void ae2bc$updateProductExtractionButton(CallbackInfo ci) {
        PatternProviderScreen<?> screen = (PatternProviderScreen<?>) (Object) this;
        ae2bc$productExtractionButton.visible = ((PatternProviderMenuExtension) screen.getMenu())
                .ae2bc$hasProductExtractionCard();
    }
}
