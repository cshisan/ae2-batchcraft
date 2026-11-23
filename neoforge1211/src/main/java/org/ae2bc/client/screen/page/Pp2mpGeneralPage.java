package org.ae2bc.client.screen.page;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.component.SelectableAE2Button;
import org.ae2bc.client.screen.component.TextSwitch;
import org.ae2bc.client.screen.component.ValidatedIntegerField;
import org.ae2bc.client.screen.page.access.GeneralAttrAccess;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.screen.Route;
import org.ae2bc.core.unit.ReturnMode;

public final class Pp2mpGeneralPage extends AbstractPp2mpPage {
    private final SyncAttrAccess syncAccess;
    private final GeneralAttrAccess generaAccess;
    private TextSwitch syncSwitch;
    private SelectableAE2Button strictButton;
    private AE2Button unblockedButton;
    private ValidatedIntegerField extractionIntervalInput;
    private ValidatedIntegerField extractionQuantityInput;

    public Pp2mpGeneralPage(SyncAttrAccess syncAccess, GeneralAttrAccess generaAccess) {
        super(
                Route.GENERAL, 134,
                new FeatureComponent(
                        Component.translatable("gui.ae2_batchcraft.page.general.return_mode.title"),
                        43, 74, null
                ),
                new FeatureComponent(
                        Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.parameters"),
                        85, 126, Pp2mpGeneralPage::drawExtractionLabels
                )
        );
        this.syncAccess = syncAccess;
        this.generaAccess = generaAccess;
    }

    private static void drawExtractionLabels(GuiGraphics graphics, FeatureComponent.ContentBounds bounds) {
        int labelX = bounds.left() + 4;
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.interval"),
                labelX, bounds.top() + 2, 0xFF404040, false
        );
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.quantity"),
                labelX, bounds.top() + 18, 0xFF404040, false
        );

        // 输入框相对坐标为 x=100，宽度为 36，单位与输入框间隔 3。
        int unitX = bounds.left() + 128;
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.tick"),
                unitX, bounds.top() + 2, 0xFF404040, false
        );

        graphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.items"),
                unitX, bounds.top() + 18, 0xFF404040, false
        );
    }

    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (strictButton == null) {
            this.syncSwitch = this.addWidget("general_sync", new TextSwitch(
                    context.getStyle(),
                    () -> {
                        if (this.syncAccess != null) {
                            this.syncAccess.setSyncEnabled(this.syncSwitch.isSelected());
                        }
                    },
                    Component.translatable("gui.ae2_batchcraft.sync_switch")
            ), TextSwitch.class);
            this.strictButton = this.addWidget("general_return_strict", new SelectableAE2Button(
                    Component.translatable("gui.ae2_batchcraft.page.general.return_mode.strict_full.button"),
                    () -> this.generaAccess.updateGeneral(old -> {
                        ReturnMode returnMode = old.returnMode().next();
                        return new GeneralPageConfig(returnMode, old.extractionParam(), old.blocking());
                    })
            ), SelectableAE2Button.class);

            this.unblockedButton = this.addButton(
                    "general_return_unblocked",
                    Component.translatable("gui.ae2_batchcraft.page.general.return_mode.unblocked"),
                    () -> this.generaAccess.updateGeneral(old -> new GeneralPageConfig(
                            ReturnMode.UNBLOCKED, old.extractionParam(), old.blocking()
                    ))
            );

            this.extractionIntervalInput = this.addWidget("extraction_interval", new ValidatedIntegerField(
                    context.getStyle(), Minecraft.getInstance().font,
                    0, 0, 36, 12,
                    Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.interval"),
                    () -> ProductExtractionConfigConstants.INTERVAL_MIN,
                    () -> ProductExtractionConfigConstants.INTERVAL_MAX,
                    value -> {
                        GeneralPageConfig oldConfig = this.generaAccess.general();
                        ProductExtractionParam oldParam = oldConfig.extractionParam();
                        this.generaAccess.updateGeneral(ignored -> new GeneralPageConfig(
                                oldConfig.returnMode(),
                                ProductExtractionParam.of((short) value, oldParam.getQuantity()), oldConfig.blocking()
                        ));
                    }
            ), ValidatedIntegerField.class);
            this.extractionQuantityInput = this.addWidget("extraction_quantity", new ValidatedIntegerField(
                    context.getStyle(), Minecraft.getInstance().font,
                    0, 0, 36, 12,
                    Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.quantity"),
                    () -> ProductExtractionConfigConstants.QUANTITY_MIN,
                    () -> ProductExtractionConfigConstants.QUANTITY_MAX,
                    value -> {
                        GeneralPageConfig oldConfig = this.generaAccess.general();
                        ProductExtractionParam oldParam = oldConfig.extractionParam();
                        this.generaAccess.updateGeneral(ignored -> new GeneralPageConfig(
                                oldConfig.returnMode(),
                                ProductExtractionParam.of(oldParam.getInterval(), value), oldConfig.blocking()
                        ));
                    }
            ), ValidatedIntegerField.class);
        }

        GeneralPageConfig general = this.generaAccess.general();
        if (general != null) {
            extractionIntervalInput.syncValue(general.extractionParam().getInterval());
            extractionQuantityInput.syncValue(general.extractionParam().getQuantity());
        }
        this.layout();
    }

    private void updateStrictButtonMessage(ReturnMode mode) {
        ReturnMode displayMode = mode == ReturnMode.UNBLOCKED ? ReturnMode.STRICT_FULL : mode;
        String key = "gui.ae2_batchcraft.page.general.return_mode." + displayMode.name;
        this.strictButton.setMessage(Component.translatable(key + ".button"));
        this.strictButton.setTooltip(Tooltip.create(Component.translatable(key)));
    }

    @Override
    public void commit() {
        extractionIntervalInput.commitValue();
        extractionQuantityInput.commitValue();
    }

    @Override
    public void update() {
        boolean supported = this.syncAccess != null;
        boolean synced = supported && this.syncAccess.isSyncEnabled();
        this.syncSwitch.visible = supported;
        this.syncSwitch.active = supported;
        syncSwitch.setSelected(synced);

        this.extractionIntervalInput.active = !synced;
        this.extractionQuantityInput.active = !synced;

        GeneralPageConfig general = this.generaAccess.general();
        ReturnMode returnMode = general.returnMode();
        this.updateStrictButtonMessage(returnMode);
        strictButton.active = !synced;
        this.strictButton.setSelected(returnMode != ReturnMode.UNBLOCKED);
        unblockedButton.active = !synced && returnMode != ReturnMode.UNBLOCKED;

        extractionIntervalInput.syncValue(general.extractionParam().getInterval());
        extractionQuantityInput.syncValue(general.extractionParam().getQuantity());
    }

    @Override
    public void layout() {
        if (this.strictButton == null) {
            return;
        }
        int syncWidth = syncSwitch.fitToMessage();
        syncSwitch.setX(context.getLeftPos() + context.getImageWidth() - 18 - syncWidth);
        syncSwitch.setY(context.getTopPos() + 21);
        super.setWidgetBounds(strictButton, 12, 50, 71, 18);
        this.setWidgetBounds(unblockedButton, 89, 50, 71, 18);
        this.setWidgetBounds(this.extractionIntervalInput, 100, 94, 36, 12);
        this.setWidgetBounds(this.extractionQuantityInput, 100, 110, 36, 12);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            extractionIntervalInput.commitValue();
            extractionQuantityInput.commitValue();
        }
        super.setVisible(visible);
    }
}
