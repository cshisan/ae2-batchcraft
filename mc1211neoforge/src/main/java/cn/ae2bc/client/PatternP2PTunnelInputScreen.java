package cn.ae2bc.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
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
    private final VerticallyAlignedCheckbox breakRecovery;
    private final Map<RedstoneOutputMode, AE2Button> redstoneModeButtons =
            new EnumMap<>(RedstoneOutputMode.class);
    private final Map<TransferPortOutputMode, AE2Button> transferModeButtons =
            new EnumMap<>(TransferPortOutputMode.class);
    private final IconButton resetTaskToolbar;
    private final IconButton dispatchModeToolbar;
    private final IconButton taskAllocationModeToolbar;
    private final AE2Button singleSlotMode;
    private final VerticallyAlignedCheckbox productExtraction;
    private ValidatedIntegerField strengthInput;
    private ValidatedIntegerField pulseTimeInput;
    private ValidatedIntegerField pulsePeriodInput;
    private ProductExtractionControls extractionControls;

    public PatternP2PTunnelInputScreen(PatternP2PTunnelInputMenu menu, Inventory playerInventory,
                                         Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
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
        productExtraction = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.product_extraction.enabled"), false);
        widgets.add("productExtraction", productExtraction);
        productExtraction.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.enabled.tooltip")));
        productExtraction.setChangeListener(() ->
                menu.setProductExtractionEnabled(productExtraction.isSelected()));
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
        dispatchModeToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.PRIORITY, 0.8f, 1, 0,
                ignored ->
                menu.setDispatchMode(menu.dispatchMode == PatternDispatchMode.FULL_DISPATCH
                        ? PatternDispatchMode.BATCH_DISTRIBUTION
                        : PatternDispatchMode.FULL_DISPATCH));
        dispatchModeToolbar.setMessage(Component.translatable("gui.ae2_batchcraft.dispatch_mode"));
        addToRightToolbar("dispatchModeToolbar", dispatchModeToolbar);
        taskAllocationModeToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.S_PROCESSOR,
                0.6f, 0, 0,
                ignored -> menu.setTaskAllocationMode(menu.taskAllocationMode.next()));
        taskAllocationModeToolbar.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_allocation_mode"));
        addToRightToolbar("taskAllocationModeToolbar", taskAllocationModeToolbar);
        resetTaskToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.SCHEDULING_DEFAULT, 0.9f, 0, 0,
                ignored ->
                TaskResetConfirmation.open(this, Component.translatable(
                        "gui.ae2_batchcraft.reset_task.confirm.input"), menu::resetTaskState));
        resetTaskToolbar.setMessage(Component.translatable("gui.ae2_batchcraft.reset_task.tooltip"));
        addToRightToolbar("resetTaskToolbar", resetTaskToolbar);
        singleSlotMode = widgets.addButton("singleSlotMode", Component.empty(),
                () -> menu.setOutputSlotSharingMode(menu.outputSlotSharingMode.next()));
        singleSlotMode.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.single_slot.tooltip")));
    }

    @Override
    protected void init() {
        super.init();
        breakRecovery.fitToMessage();
        breakRecovery.setX(leftPos + 12);
        breakRecovery.setY(topPos + 52);
        int productExtractionWidth = productExtraction.fitToMessage();
        productExtraction.setX(leftPos
                + DashedSectionRenderer.trailingContentX(imageWidth, productExtractionWidth));
        productExtraction.setY(topPos + 79);
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
        completeInitialLayout();
    }

    @Override
    protected int getPageHeight(Page page) {
        return switch (page) {
            case COMMON -> 186;
            case TRANSFER -> 82;
            case BREAK -> 82;
            case REDSTONE -> 166;
            case ENERGY -> 82;
        };
    }

    @Override
    protected boolean supportsEnergyPage() {
        return false;
    }

    @Override
    protected void updatePageVisibility() {
        boolean common = isPage(Page.COMMON);
        boolean transfer = isPage(Page.TRANSFER);
        boolean breakPort = isPage(Page.BREAK);
        boolean redstonePort = isPage(Page.REDSTONE);

        strictButton.visible = common;
        unblockedButton.visible = common;
        resetTaskToolbar.visible = true;
        dispatchModeToolbar.visible = true;
        taskAllocationModeToolbar.visible = true;
        singleSlotMode.visible = common;
        productExtraction.visible = common;
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
        breakRecovery.setSelected(menu.breakRecovery);
        productExtraction.setSelected(menu.productExtractionEnabled);
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
        singleSlotMode.setMessage(Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.single_slot." +
                        menu.outputSlotSharingMode.getSerializedName()));
        singleSlotMode.active = true;
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
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"),
                    productExtraction.getWidth(),
                    offsetX, offsetY, imageWidth, 85, 137);
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot"),
                    offsetX, offsetY, imageWidth, 146, 177);
        } else if (isPage(Page.TRANSFER)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else if (isPage(Page.BREAK)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling"),
                    offsetX, offsetY, imageWidth, 43, 74);
        } else {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode"),
                    offsetX, offsetY, imageWidth, 43, 74);
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters"),
                    offsetX, offsetY, imageWidth, 85, 158);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (isPage(Page.COMMON)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.return_configuration"), 39);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"), 81);
            extractionControls.drawUnits(graphics, font, leftPos);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot"), 142);
        } else if (isPage(Page.TRANSFER)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode"), 39);
        } else if (isPage(Page.BREAK)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling"), 39);
        } else {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode"), 39);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters"), 81);
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
