package cn.ae2bc.client;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.gui.components.Button;
import appeng.client.gui.widgets.TabButton;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
import cn.ae2bc.logic.EnergyDistributionMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.menu.PatternP2PTunnelInputMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public final class PatternP2PTunnelInputScreen extends PatternP2PUnitPagedScreen<PatternP2PTunnelInputMenu> {
    private final Button strictButton;
    private final Button unblockedButton;
    private final VerticallyAlignedCheckbox breakRecovery;
    private final Map<RedstoneOutputMode, Button> redstoneModeButtons =
            new EnumMap<>(RedstoneOutputMode.class);
    private final Map<TransferPortOutputMode, Button> transferModeButtons =
            new EnumMap<>(TransferPortOutputMode.class);
    private final TabButton resetTaskToolbar;
    private final TabButton taskAllocationModeToolbar;
    private final Map<OutputSlotSharingMode, Button> singleSlotModeButtons =
            new EnumMap<>(OutputSlotSharingMode.class);
    private final Button productExtractionEnabled;
    private final Button productExtractionDisabled;
    private final Button energyDistributionMode;
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
        breakRecovery = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery"));
        widgets.add("breakRecovery", breakRecovery);
        PatternP2PUnitConfigScreenSupport.applyBreakPortTooltip(breakRecovery);
        breakRecovery.setChangeListener(() -> menu.setBreakRecovery(breakRecovery.isSelected()));
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
        resetTaskToolbar = new TabButton(appeng.client.gui.Icon.SCHEDULING_DEFAULT,
                Component.translatable("gui.ae2_batchcraft.reset_task.tooltip"), ignored ->
                TaskResetConfirmation.open(this, Component.translatable(
                        "gui.ae2_batchcraft.reset_task.confirm.input"), menu::resetTaskState));
        addToRightToolbar("resetTaskToolbar", resetTaskToolbar);
        taskAllocationModeToolbar = new ScaledTabButton(appeng.client.gui.Icon.FUZZY_PERCENT_99,
                1.0f, Component.translatable("gui.ae2_batchcraft.task_allocation_mode"),
                ignored -> menu.setTaskAllocationMode(menu.taskAllocationMode.next()));
        addToRightToolbar("taskAllocationModeToolbar", taskAllocationModeToolbar);
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
        breakRecovery.fitToMessage();
        breakRecovery.setX(leftPos + 12);
        breakRecovery.setY(topPos + 52);
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
        PatternP2PUnitConfigScreenSupport.applyRedstonePortTooltips(redstoneModeButtons.values(),
                strengthInput, pulseTimeInput, pulsePeriodInput);
        updatePageVisibility();
    }

    @Override
    protected int getPageHeight(Page page) {
        return switch (page) {
            case COMMON -> 146;
            case OUTPUT_COMMON, UNIT_COMMON, TRANSFER -> 82;
            case BREAK -> 82;
            case REDSTONE -> 166;
            case ENERGY -> 82;
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
        taskAllocationModeToolbar.visible = true;
        for (var button : singleSlotModeButtons.values()) button.visible = unitCommon;
        productExtractionEnabled.visible = outputCommon;
        productExtractionDisabled.visible = outputCommon;
        energyDistributionMode.visible = isPage(Page.ENERGY);
        breakRecovery.visible = breakPort;
        for (var button : redstoneModeButtons.values()) {
            button.visible = redstonePort;
        }
        for (var button : transferModeButtons.values()) button.visible = transfer;
        if (strengthInput != null) {
            strengthInput.visible = redstonePort;
            pulseTimeInput.visible = redstonePort;
            pulsePeriodInput.visible = redstonePort;
        }
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
        breakRecovery.setSelected(menu.breakRecovery);
        productExtractionEnabled.active = !menu.productExtractionEnabled;
        productExtractionDisabled.active = menu.productExtractionEnabled;
        extractionControls.sync(menu.productExtractionInterval, menu.productExtractionAmount);
        for (var entry : redstoneModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.redstoneMode;
        }
        for (var entry : transferModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.transferPortOutputMode;
        }
        for (var entry : singleSlotModeButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.outputSlotSharingMode;
        }
        energyDistributionMode.setMessage(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode."
                        + menu.energyDistributionMode.getSerializedName()));
        energyDistributionMode.active = true;
        TaskAllocationMode nextMode = menu.taskAllocationMode.next();
        taskAllocationModeToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_allocation_mode.switch.tooltip",
                taskAllocationModeName(menu.taskAllocationMode),
                Component.translatable("gui.ae2_batchcraft.task_allocation_mode."
                        + menu.taskAllocationMode.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip"),
                taskAllocationModeName(nextMode)));
        strengthInput.syncValue(menu.redstoneStrength);
        pulseTimeInput.syncValue(menu.pulseWidth);
        pulsePeriodInput.syncValue(menu.pulsePeriod);
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
