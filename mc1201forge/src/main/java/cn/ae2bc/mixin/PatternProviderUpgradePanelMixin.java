package cn.ae2bc.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import cn.ae2bc.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the dedicated product-extraction slot to the standard AE2 upgrade panel used by pattern-provider screens.
 */
@Mixin(AEBaseScreen.class)
public abstract class PatternProviderUpgradePanelMixin {
    @Unique
    private UpgradesPanel ae2bc$tooltipExtendedPanel;

    @Inject(method = "init", at = @At("HEAD"))
    private void ae2bc$createProductExtractionUpgradePanel(CallbackInfo ci) {
        if (!((Object) this instanceof PatternProviderScreen<?> screen)) {
            return;
        }

        var widgets = ((AEBaseScreenAccessor) screen).ae2bc$getWidgets();
        var existingPanel = ((WidgetContainerAccessor) widgets).ae2bc$getCompositeWidgetsById().get("upgrades");
        if (existingPanel instanceof UpgradesPanel upgradesPanel) {
            ae2bc$extendUpgradeTooltip(upgradesPanel);
            return;
        }

        if (existingPanel == null) {
            var upgradesPanel = new UpgradesPanel(screen.getMenu().getSlots(SlotSemantics.UPGRADE),
                    this::ae2bc$getProductExtractionTooltip);
            widgets.add("upgrades", upgradesPanel);
            ae2bc$tooltipExtendedPanel = upgradesPanel;
        }
    }

    @Unique
    private void ae2bc$extendUpgradeTooltip(UpgradesPanel upgradesPanel) {
        var panelAccessor = (UpgradesPanelAccessor) (Object) upgradesPanel;
        if (ae2bc$tooltipExtendedPanel != upgradesPanel) {
            var originalSupplier = panelAccessor.ae2bc$getTooltipSupplier();
            panelAccessor.ae2bc$setTooltipSupplier(() -> {
                var lines = new ArrayList<>(originalSupplier.get());
                var heading = GuiText.CompatibleUpgrades.text();
                if (!lines.contains(heading)) {
                    lines.add(0, heading);
                }
                var productExtractionLine = ae2bc$getProductExtractionUpgradeLine();
                if (!lines.contains(productExtractionLine)) {
                    lines.add(productExtractionLine);
                }
                return lines;
            });
            ae2bc$tooltipExtendedPanel = upgradesPanel;
        }
    }

    @Unique
    private List<Component> ae2bc$getProductExtractionTooltip() {
        return List.of(GuiText.CompatibleUpgrades.text(), ae2bc$getProductExtractionUpgradeLine());
    }

    @Unique
    private Component ae2bc$getProductExtractionUpgradeLine() {
        return GuiText.CompatibleUpgrade.text(
                ModContent.PRODUCT_EXTRACTION_CARD.get().getDescription(), 1)
                .withStyle(ChatFormatting.GRAY);
    }
}
