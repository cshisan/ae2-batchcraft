package cn.ae2bc.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.PatternDispatchMode;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public final class PatternP2PTunnelInputScreen extends PatternP2PUnitPagedScreen<PatternP2PTunnelInputMenu> {
    private final AE2Button strictButton;
    private final AE2Button unblockedButton;
    private final AE2Button breakRecovery;
    private final Map<RedstoneOutputMode, AE2Button> redstoneModeButtons =
            new EnumMap<>(RedstoneOutputMode.class);
    private final Map<TransferPortOutputMode, AE2Button> transferModeButtons =
            new EnumMap<>(TransferPortOutputMode.class);
    private final IconButton resetTaskToolbar;
    private final IconButton dispatchModeToolbar;
    private final IconButton taskAllocationModeToolbar;
    private final Map<OutputSlotSharingMode, AE2Button> singleSlotModeButtons =
            new EnumMap<>(OutputSlotSharingMode.class);
    private final AE2Button energyDistributionMode;
    private final AE2Button productExtractionEnabled;
    private final AE2Button productExtractionDisabled;
    private ValidatedIntegerField strengthInput;
    private ValidatedIntegerField pulseTimeInput;
    private ValidatedIntegerField pulsePeriodInput;
    private ProductExtractionControls extractionControls;

    public PatternP2PTunnelInputScreen(PatternP2PTunnelInputMenu menu, Inventory playerInventory,
                                         Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style, true, PageGroup.OUTPUT);
        strictButton = widgets.addButton("returnStrict",
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.return_mode.strict"),
                () -> menu.setReturnMode(ReturnMode.STRICT));
        strictButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.return_mode.strict.tooltip")));
        unblockedButton = widgets.addButton("returnUnblocked",
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.return_mode.unblocked"),
                () -> menu.setReturnMode(ReturnMode.UNBLOCKED));
        unblockedButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.return_mode.unblocked.tooltip")));
        productExtractionEnabled = widgets.addButton("productExtractionEnabled",
                Component.translatable("gui.ae2_batchcraft.enabled"),
                () -> menu.setProductExtractionEnabled(true));
        productExtractionEnabled.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.enabled.tooltip")));
        productExtractionDisabled = widgets.addButton("productExtractionDisabled",
                Component.translatable("gui.ae2_batchcraft.disabled"),
                () -> menu.setProductExtractionEnabled(false));
        productExtractionDisabled.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.enabled.tooltip")));
        breakRecovery = widgets.addButton("breakRecovery", Component.empty(),
                () -> menu.setBreakRecovery(!menu.breakRecovery));
        PatternP2PUnitConfigScreenSupport.applyBreakPortTooltip(breakRecovery);
        addRedstoneModeButton("redstoneSingle", RedstoneOutputMode.SINGLE_TRIGGER);
        addRedstoneModeButton("redstonePeriodic", RedstoneOutputMode.PERIODIC_PULSE);
        addRedstoneModeButton("redstoneContinuous", RedstoneOutputMode.CONTINUOUS);
        for (TransferPortOutputMode mode : TransferPortOutputMode.values()) {
            var button = widgets.addButton("transfer" + camel(mode.getSerializedName()),
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName()),
                    () -> menu.setTransferPortOutputMode(mode));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName() + ".tooltip")));
            transferModeButtons.put(mode, button);
        }
        dispatchModeToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.PRIORITY, 0.8f,
                ignored ->
                menu.setDispatchMode(menu.dispatchMode == PatternDispatchMode.FULL_DISPATCH
                        ? PatternDispatchMode.BATCH_DISTRIBUTION
                        : PatternDispatchMode.FULL_DISPATCH));
        dispatchModeToolbar.setMessage(Component.translatable("gui.ae2_batchcraft.dispatch_mode"));
        addToRightToolbar("dispatchModeToolbar", dispatchModeToolbar);
        taskAllocationModeToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.S_PROCESSOR, 0.6f,
                ignored -> menu.setTaskAllocationMode(menu.taskAllocationMode.next()));
        taskAllocationModeToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_allocation_mode"));
        addToRightToolbar("taskAllocationModeToolbar", taskAllocationModeToolbar);
        resetTaskToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.SCHEDULING_DEFAULT, 0.9f,
                ignored ->
                TaskResetConfirmation.open(this, Component.translatable(
                        "gui.ae2_batchcraft.reset_task.confirm.input"), menu::resetTaskState));
        resetTaskToolbar.setMessage(Component.translatable("gui.ae2_batchcraft.reset_task.tooltip"));
        addToRightToolbar("resetTaskToolbar", resetTaskToolbar);
        for (OutputSlotSharingMode mode : OutputSlotSharingMode.values()) {
            var button = widgets.addButton("singleSlot" + camel(mode.getSerializedName()),
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.single_slot."
                            + mode.getSerializedName()),
                    () -> menu.setOutputSlotSharingMode(mode));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.single_slot.tooltip")));
            singleSlotModeButtons.put(mode, button);
        }
        energyDistributionMode = widgets.addButton("energyDistributionMode", Component.empty(),
                () -> menu.setEnergyDistributionMode(menu.energyDistributionMode.next()));
    }

    @Override
    protected void init() {
        super.init();
        extractionControls = ProductExtractionControls.create(font, leftPos, topPos,
                this::addRenderableWidget, menu::setProductExtractionInterval, menu::setProductExtractionAmount);
        strengthInput = addRenderableWidget(new ValidatedIntegerField(font,
                leftPos + 104, topPos + 92, 36, 16,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength"),
                () -> 0, () -> 15, menu::setRedstoneStrength));
        pulseTimeInput = addRenderableWidget(new ValidatedIntegerField(font,
                leftPos + 104, topPos + 113, 36, 16,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.pulse_width"),
                () -> 1, () -> menu.pulsePeriod, menu::setPulseWidth));
        pulsePeriodInput = addRenderableWidget(new ValidatedIntegerField(font,
                leftPos + 104, topPos + 134, 36, 16,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.pulse_period"),
                () -> 1, () -> PatternP2PUnitConfiguration.MAX_PULSE_TICKS, menu::setPulsePeriod));
        PatternP2PUnitConfigScreenSupport.applyRedstonePortTooltips(redstoneModeButtons.values());
        updatePageVisibility();
        completeInitialLayout();
    }

    @Override
    protected int getPageHeight(Page page) {
        return switch (page) {
            case COMMON -> 146;
            case OUTPUT_COMMON, UNIT_COMMON, TRANSFER, BREAK, ENERGY -> 82;
            case REDSTONE -> 166;
        };
    }

    @Override
    protected void updatePageVisibility() {
        boolean common = isPage(Page.COMMON);
        boolean outputCommon = isPage(Page.OUTPUT_COMMON);
        boolean unitCommon = isPage(Page.UNIT_COMMON);
        boolean transfer = isPage(Page.TRANSFER);
        boolean breakPort = isPage(Page.BREAK);
        boolean redstonePort = isPage(Page.REDSTONE);

        strictButton.visible = common;
        unblockedButton.visible = common;
        resetTaskToolbar.visible = true;
        dispatchModeToolbar.visible = true;
        taskAllocationModeToolbar.visible = true;
        for (var button : singleSlotModeButtons.values()) {
            button.visible = unitCommon;
        }
        productExtractionEnabled.visible = outputCommon;
        productExtractionDisabled.visible = outputCommon;
        energyDistributionMode.visible = isPage(Page.ENERGY);
        breakRecovery.visible = breakPort;
        for (var button : redstoneModeButtons.values()) {
            button.visible = redstonePort;
        }
        if (strengthInput != null) {
            strengthInput.visible = redstonePort;
            pulseTimeInput.visible = redstonePort;
            pulsePeriodInput.visible = redstonePort;
        }
        for (var button : transferModeButtons.values()) button.visible = transfer;
        if (extractionControls != null) {
            extractionControls.setVisible(common);
        }

        setTextHidden("extraction_interval", !common);
        setTextHidden("extraction_amount", !common);

        setTextHidden("strength", !redstonePort);
        setTextHidden("pulse_width", !redstonePort);
        setTextHidden("pulse_width_unit", !redstonePort);
        setTextHidden("pulse_period", !redstonePort);
        setTextHidden("pulse_period_unit", !redstonePort);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        strictButton.active = menu.returnMode != ReturnMode.STRICT;
        unblockedButton.active = menu.returnMode != ReturnMode.UNBLOCKED;
        breakRecovery.setMessage(Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.break_recovery.value",
                Component.translatable(menu.breakRecovery
                        ? "gui.ae2_batchcraft.enabled" : "gui.ae2_batchcraft.disabled")));
        breakRecovery.active = true;
        productExtractionEnabled.active = !menu.productExtractionEnabled;
        productExtractionDisabled.active = menu.productExtractionEnabled;
        if (extractionControls != null) {
            extractionControls.sync(menu.productExtractionInterval, menu.productExtractionAmount);
        }
        for (var entry : redstoneModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.redstoneMode;
        }
        for (var entry : transferModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.transferPortOutputMode;
        }
        if (strengthInput != null) {
            strengthInput.syncValue(menu.redstoneStrength);
            pulseTimeInput.syncValue(menu.pulseWidth);
            pulsePeriodInput.syncValue(menu.pulsePeriod);
        }
        PatternDispatchMode nextMode = menu.dispatchMode == PatternDispatchMode.FULL_DISPATCH
                ? PatternDispatchMode.BATCH_DISTRIBUTION
                : PatternDispatchMode.FULL_DISPATCH;
        dispatchModeToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.dispatch_mode.switch.tooltip",
                dispatchModeName(menu.dispatchMode), dispatchModeName(nextMode)));
        TaskAllocationMode nextAllocationMode = menu.taskAllocationMode.next();
        taskAllocationModeToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_allocation_mode.switch.tooltip",
                taskAllocationModeName(menu.taskAllocationMode),
                Component.translatable("gui.ae2_batchcraft.task_allocation_mode."
                        + menu.taskAllocationMode.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip"),
                taskAllocationModeName(nextAllocationMode)));
        for (var entry : singleSlotModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.outputSlotSharingMode;
        }
        energyDistributionMode.setMessage(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode."
                        + menu.energyDistributionMode.getSerializedName()));
        energyDistributionMode.active = true;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        if (isPage(Page.COMMON)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.return_configuration"),
                    offsetX, offsetY, imageWidth, 43, 74);
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.parameters"),
                    offsetX, offsetY, imageWidth, 85, 137);
        } else if (isPage(Page.OUTPUT_COMMON)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else if (isPage(Page.UNIT_COMMON)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else if (isPage(Page.TRANSFER)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else if (isPage(Page.BREAK)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else if (isPage(Page.REDSTONE)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode"),
                    offsetX, offsetY, imageWidth, 43, 74);
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters"),
                    offsetX, offsetY, imageWidth, 85, 158);
        } else {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"),
                    offsetX, offsetY, imageWidth, 43, 74);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (isPage(Page.COMMON)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.return_configuration"), 39);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.parameters"), 81);
            extractionControls.drawUnits(graphics, font, leftPos);
        } else if (isPage(Page.OUTPUT_COMMON)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"), 39);
        } else if (isPage(Page.UNIT_COMMON)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot"), 39);
        } else if (isPage(Page.TRANSFER)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode"), 39);
        } else if (isPage(Page.BREAK)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling"), 39);
        } else if (isPage(Page.REDSTONE)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode"), 39);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters"), 81);
        } else {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"), 39);
        }
    }

    private void addRedstoneModeButton(String widgetId, RedstoneOutputMode mode) {
        var button = widgets.addButton(widgetId, Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode." + mode.getSerializedName()),
                () -> menu.setRedstoneMode(mode));
        redstoneModeButtons.put(mode, button);
    }

    private static Component dispatchModeName(PatternDispatchMode mode) {
        return Component.translatable("gui.ae2_batchcraft.dispatch_mode."
                + (mode == PatternDispatchMode.BATCH_DISTRIBUTION
                ? "batch_distribution" : "full_dispatch"));
    }

    private static Component taskAllocationModeName(TaskAllocationMode mode) {
        return Component.translatable("gui.ae2_batchcraft.task_allocation_mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String camel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (char c : value.toCharArray()) {
            if (c == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return result.toString();
    }

}
