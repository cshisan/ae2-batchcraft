package cn.ae2bc.client;

import net.minecraft.client.gui.components.Button;
import appeng.client.gui.widgets.AECheckbox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

final class PatternP2PUnitConfigScreenSupport {
    private PatternP2PUnitConfigScreenSupport() {
    }

    static void applyBreakPortTooltip(AECheckbox checkbox) {
        checkbox.setTooltip(Tooltip.create(Component
                .translatable("gui.ae2_batchcraft.pattern_p2p_unit.port.break.scope")
                .append("\n")
                .append(Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery.tooltip"))));
    }

    static void applyRedstonePortTooltips(Iterable<? extends Button> modeButtons,
                                          AbstractWidget... valueWidgets) {
        Component scope = Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.port.redstone.scope");
        Tooltip tooltip = Tooltip.create(scope);
        Tooltip modeTooltip = Tooltip.create(scope.copy().append("\n").append(
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.tooltip")));
        for (Button modeButton : modeButtons) {
            modeButton.setTooltip(modeTooltip);
        }
        for (AbstractWidget widget : valueWidgets) {
            widget.setTooltip(tooltip);
        }
    }
}
