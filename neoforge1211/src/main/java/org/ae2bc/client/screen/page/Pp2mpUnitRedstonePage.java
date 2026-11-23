package org.ae2bc.client.screen.page;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.component.ValidatedIntegerField;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.client.screen.page.access.UnitAttrAccess;
import org.ae2bc.core.constant.UnitConfigConstants;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.screen.Route;
import org.ae2bc.core.unit.RedstoneMode;
import org.ae2bc.core.unit.RedstonePortParam;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public final class Pp2mpUnitRedstonePage extends AbstractPp2mpPage {
    private static final int HEIGHT = 150;
    private final UnitAttrAccess unitAccess;
    private final SyncAttrAccess syncAccess;
    private final AbstractWidget[] modeButtons = new AbstractWidget[3];
    private ValidatedIntegerField strengthInput;
    private ValidatedIntegerField pulseWidthInput;
    private ValidatedIntegerField pulsePeriodInput;

    public Pp2mpUnitRedstonePage(UnitAttrAccess unitAccess, SyncAttrAccess syncAccess) {
        super(
                Route.UNIT_REDSTONE, HEIGHT,
                new FeatureComponent(
                        Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.title"),
                        43, 74, null
                ),
                new FeatureComponent(
                        Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.signal_parameters"),
                        85, 142, Pp2mpUnitRedstonePage::drawParameterLabels
                )
        );
        this.unitAccess = unitAccess;
        this.syncAccess = syncAccess;
    }

    private static void drawParameterLabels(GuiGraphics graphics, FeatureComponent.ContentBounds bounds) {
        Font font = Minecraft.getInstance().font;
        int labelX = bounds.left() + 4;
        graphics.drawString(
                font, Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.redstone_strength"),
                labelX, bounds.top() + 2, 0xFF404040, false
        );
        graphics.drawString(
                font, Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.pulse_width"),
                labelX, bounds.top() + 18, 0xFF404040, false
        );
        graphics.drawString(
                font, Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.tick"),
                bounds.left() + 131, bounds.top() + 18, 0xFF404040, false)
        ;
        graphics.drawString(
                font, Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.pulse_period"),
                labelX, bounds.top() + 34, 0xFF404040, false
        );
        graphics.drawString(
                font, Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param.tick"),
                bounds.left() + 131, bounds.top() + 34, 0xFF404040, false
        );
    }

    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (this.modeButtons[0] == null) {
            this.modeButtons[0] = this.addButton(
                    "unit_redstone_single",
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.single_trigger.button"),
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.single_trigger"),
                    () -> this.switchRedstoneMode(RedstoneMode.ONCE)
            );
            this.modeButtons[1] = this.addButton(
                    "unit_redstone_periodic",
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.periodic_pulse.button"),
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.periodic_pulse"),
                    () -> this.switchRedstoneMode(RedstoneMode.PULSE)
            );
            this.modeButtons[2] = this.addButton(
                    "unit_redstone_continuous",
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.continuous.button"),
                    Component.translatable("gui.ae2_batchcraft.page.unit.redstone.redstone_mode.continuous"),
                    () -> this.switchRedstoneMode(RedstoneMode.CONTINUOUS)
            );

            this.strengthInput = this.addParameter("strength", "redstone_strength",
                    UnitConfigConstants.REDSTONE_STRENGTH_MIN, UnitConfigConstants.REDSTONE_STRENGTH_MAX,
                    (old, value) -> RedstonePortParam.of(old.getMode(), value, old.getPulseWidth(), old.getPulsePeriod()));
            this.pulseWidthInput = this.addParameter("pulse_width", "pulse_width",
                    1, UnitConfigConstants.REDSTONE_PULSE_PERIOD_MAX,
                    (old, value) -> RedstonePortParam.of(old.getMode(), old.getStrength(), value, old.getPulsePeriod()));
            this.pulsePeriodInput = this.addParameter("pulse_period", "pulse_period",
                    1, UnitConfigConstants.REDSTONE_PULSE_PERIOD_MAX,
                    (old, value) -> RedstonePortParam.of(old.getMode(), old.getStrength(), old.getPulseWidth(), value));
        }

        UnitPageConfig unitConfig = this.unitAccess.unit();
        RedstonePortParam redstonePortParam = unitConfig.redstonePortParam();
        this.strengthInput.syncValue(redstonePortParam.getStrength());
        this.pulseWidthInput.syncValue(redstonePortParam.getPulseWidth());
        this.pulsePeriodInput.syncValue(redstonePortParam.getPulsePeriod());

        this.layout();
    }

    private void switchRedstoneMode(RedstoneMode mode) {
        this.updateRedstone(old -> RedstonePortParam.of(
                mode, old.getStrength(), old.getPulseWidth(), old.getPulsePeriod()));
    }

    /* 各数值控件共用校验和配置更新流程，只替换对应的红石参数。 */
    private ValidatedIntegerField addParameter(
            String id, String label, int min, int max,
            BiFunction<RedstonePortParam, Short, RedstonePortParam> updater
    ) {
        return this.addWidget("unit_redstone_" + id, new ValidatedIntegerField(
                this.context.getStyle(), Minecraft.getInstance().font, 0, 0, 36, 12,
                Component.translatable("gui.ae2_batchcraft.page.unit.redstone.param." + label),
                () -> min, () -> max,
                value -> this.updateRedstone(old -> updater.apply(old, (short) value))
        ), ValidatedIntegerField.class);
    }

    private void updateRedstone(UnaryOperator<RedstonePortParam> updater) {
        this.unitAccess.updateUnit(old -> new UnitPageConfig(
                old.breakPickup(), old.outputPortMode(),
                updater.apply(old.redstonePortParam())
        ));
    }

    @Override
    public void update() {
        UnitPageConfig unitConfig = this.unitAccess.unit();
        boolean synced = this.syncAccess != null && this.syncAccess.isSyncEnabled();
        this.modeButtons[0].active = !synced
                && !RedstoneMode.ONCE.equals(unitConfig.redstonePortParam().getMode());
        this.modeButtons[1].active = !synced
                && !RedstoneMode.PULSE.equals(unitConfig.redstonePortParam().getMode());
        this.modeButtons[2].active = !synced
                && !RedstoneMode.CONTINUOUS.equals(unitConfig.redstonePortParam().getMode());

        RedstonePortParam redstonePortParam = unitConfig.redstonePortParam();
        this.strengthInput.active = !synced;
        this.pulseWidthInput.active = !synced;
        this.pulsePeriodInput.active = !synced;
        strengthInput.syncValue(redstonePortParam.getStrength());
        pulseWidthInput.syncValue(redstonePortParam.getPulseWidth());
        pulsePeriodInput.syncValue(redstonePortParam.getPulsePeriod());
    }

    @Override
    public void layout() {
        this.layoutModeButtons(this.modeButtons);
        this.setWidgetBounds(this.strengthInput, 104, 94, 36, 12);
        this.setWidgetBounds(this.pulseWidthInput, 104, 110, 36, 12);
        this.setWidgetBounds(this.pulsePeriodInput, 104, 126, 36, 12);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            this.commit();
        }
        super.setVisible(visible);
    }

    @Override
    public void commit() {
        if (this.strengthInput == null || this.pulseWidthInput == null || this.pulsePeriodInput == null) {
            return;
        }

        strengthInput.commitValue();
        pulseWidthInput.commitValue();
        pulsePeriodInput.commitValue();
    }
}
