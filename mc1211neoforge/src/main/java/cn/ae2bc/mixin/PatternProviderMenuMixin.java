package cn.ae2bc.mixin;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.RestrictedInputSlot;
import cn.ae2bc.extension.PatternProviderExtractionExtension;
import cn.ae2bc.extension.PatternProviderMenuExtension;
import cn.ae2bc.menu.ProductExtractionMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuMixin implements PatternProviderMenuExtension {
    private static final String AE2BC_OPEN_PRODUCT_EXTRACTION = "ae2bcOpenProductExtraction";

    @Shadow @Final protected PatternProviderLogic logic;

    @Unique @GuiSync(80)
    private boolean ae2bc$productExtractionCard;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$addProductExtractionUpgradeSlot(CallbackInfo ci) {
        var upgrades = ((PatternProviderExtractionExtension) logic).ae2bc$getProductExtractionUpgrades();
        ((AEBaseMenuInvoker) this).ae2bc$addSlot(
                new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, 0),
                SlotSemantics.UPGRADE);
        ((AEBaseMenuInvoker) this).ae2bc$registerClientAction(
                AE2BC_OPEN_PRODUCT_EXTRACTION, this::ae2bc$openProductExtraction);
    }

    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void ae2bc$syncProductExtractionCard(CallbackInfo ci) {
        PatternProviderMenu menu = (PatternProviderMenu) (Object) this;
        if (!menu.isClientSide()) {
            ae2bc$productExtractionCard = ((PatternProviderExtractionExtension) logic).ae2bc$hasProductExtractionCard();
        }
    }

    @Override
    public void ae2bc$openProductExtractionSettings() {
        ((AEBaseMenuInvoker) this).ae2bc$sendClientAction(AE2BC_OPEN_PRODUCT_EXTRACTION);
    }

    @Override
    public boolean ae2bc$hasProductExtractionCard() {
        return ae2bc$productExtractionCard;
    }

    @Unique
    private void ae2bc$openProductExtraction() {
        PatternProviderMenu menu = (PatternProviderMenu) (Object) this;
        if (!menu.isClientSide()
                && ((PatternProviderExtractionExtension) logic).ae2bc$hasProductExtractionCard()
                && menu.getLocator() != null) {
            MenuOpener.open(ProductExtractionMenu.TYPE, menu.getPlayer(), menu.getLocator());
        }
    }
}
