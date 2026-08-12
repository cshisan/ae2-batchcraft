package cn.ae2bc.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import cn.ae2bc.logic.PatternP2PUnitConfiguration;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public final class PatternP2PUnitManagerScreen extends PatternP2PUnitPagedScreen<PatternP2PUnitManagerMenu> {
    private final VerticallyAlignedCheckbox syncMain;
    private final VerticallyAlignedCheckbox breakRecovery;
    private final Map<ReturnMode, AE2Button> returnButtons = new EnumMap<>(ReturnMode.class);
    private final Map<RedstoneOutputMode, AE2Button> redstoneModeButtons =
            new EnumMap<>(RedstoneOutputMode.class);
    private final AE2Button energyDistributionMode;
    private final AE2Button resetTask;
    private ValidatedIntegerField strengthInput;
    private ValidatedIntegerField pulseTimeInput;
    private ValidatedIntegerField pulsePeriodInput;
    private ProductExtractionControls extractionControls;

    public PatternP2PUnitManagerScreen(PatternP2PUnitManagerMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        syncMain = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration"));
        widgets.add("syncMain", syncMain);
        syncMain.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration.tooltip")));
        syncMain.setChangeListener(() -> menu.setSyncMain(syncMain.isSelected()));
        for (ReturnMode mode : ReturnMode.values()) {
            var button = widgets.addButton("return" + camel(mode.getSerializedName()),
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." +
                            mode.getSerializedName()),
                    () -> menu.setReturnMode(mode));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.return_mode." + mode.getSerializedName() + ".tooltip")));
            returnButtons.put(mode, button);
        }
        energyDistributionMode = widgets.addButton("energyDistributionMode", Component.empty(),
                () -> menu.setEnergyDistributionMode(menu.energyDistributionMode.next()));
        energyDistributionMode.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode.tooltip")));
        breakRecovery = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery"));
        widgets.add("breakRecovery", breakRecovery);
        PatternP2PUnitConfigScreenSupport.applyBreakPortTooltip(breakRecovery);
        breakRecovery.setChangeListener(() -> menu.setBreakRecovery(breakRecovery.isSelected()));
        addRedstoneModeButton("redstoneSingle", RedstoneOutputMode.SINGLE_TRIGGER);
        addRedstoneModeButton("redstonePeriodic", RedstoneOutputMode.PERIODIC_PULSE);
        addRedstoneModeButton("redstoneContinuous", RedstoneOutputMode.CONTINUOUS);
        resetTask = widgets.addButton("resetTask",
                Component.translatable("gui.ae2_batchcraft.reset_task"),
                () -> TaskResetConfirmation.open(this, Component.translatable(
                        "gui.ae2_batchcraft.reset_task.confirm.unit"), menu::resetTaskState));
        resetTask.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.reset_task.tooltip.unit")));
    }

    @Override
    protected void init() {
        super.init();
        int syncMainWidth = syncMain.fitToMessage();
        syncMain.setX(leftPos + imageWidth - 8 - syncMainWidth);
        syncMain.setY(topPos + 21);
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
            case COMMON -> 227;
            case BREAK -> 82;
            case REDSTONE -> 166;
        };
    }

    @Override
    protected void updatePageVisibility() {
        boolean common = isPage(Page.COMMON);
        boolean breakPort = isPage(Page.BREAK);
        boolean redstonePort = isPage(Page.REDSTONE);

        syncMain.visible = common;
        for (var button : returnButtons.values()) {
            button.visible = common;
        }
        energyDistributionMode.visible = common;
        resetTask.visible = common;
        breakRecovery.visible = breakPort;
        for (var button : redstoneModeButtons.values()) {
            button.visible = redstonePort;
        }
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
        syncMain.setSelected(menu.syncMain);
        breakRecovery.setSelected(menu.breakRecovery);
        boolean editable = !menu.syncMain;
        for (var entry : returnButtons.entrySet()) entry.getValue().active = editable && entry.getKey() != menu.returnMode;
        energyDistributionMode.setMessage(Component.translatable(
                "gui.ae2_batchcraft.energy_distribution_mode." +
                        menu.energyDistributionMode.getSerializedName()));
        for (var entry : redstoneModeButtons.entrySet()) {
            entry.getValue().active = editable && entry.getKey() != menu.redstoneMode;
        }
        breakRecovery.active = editable;
        strengthInput.setEditable(editable);
        pulseTimeInput.setEditable(editable);
        pulsePeriodInput.setEditable(editable);
        extractionControls.setEditable(editable);
        strengthInput.syncValue(menu.redstoneStrength);
        pulseTimeInput.syncValue(menu.pulseWidth);
        pulsePeriodInput.syncValue(menu.pulsePeriod);
        extractionControls.sync(menu.productExtractionInterval, menu.productExtractionAmount);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        if (isPage(Page.COMMON)) {
            drawSectionBackground(graphics, offsetX, offsetY, 43, 74,
                    "gui.ae2_batchcraft.return_configuration");
            drawSectionBackground(graphics, offsetX, offsetY, 85, 137,
                    "gui.ae2_batchcraft.product_extraction.title");
            drawSectionBackground(graphics, offsetX, offsetY, 146, 177,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration");
            drawSectionBackground(graphics, offsetX, offsetY, 188, 219,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset");
        } else if (isPage(Page.BREAK)) {
            drawSectionBackground(graphics, offsetX, offsetY, 43, 74,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling");
        } else {
            drawSectionBackground(graphics, offsetX, offsetY, 43, 74,
                    "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode");
            drawSectionBackground(graphics, offsetX, offsetY, 85, 158,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters");
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (isPage(Page.COMMON)) {
            drawSectionTitle(graphics, 39, "gui.ae2_batchcraft.return_configuration");
            drawSectionTitle(graphics, 81,
                    "gui.ae2_batchcraft.product_extraction.title");
            extractionControls.drawUnits(graphics, font, leftPos);
            drawSectionTitle(graphics, 142,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration");
            drawSectionTitle(graphics, 184,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset");
        } else if (isPage(Page.BREAK)) {
            drawSectionTitle(graphics, 39,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling");
        } else {
            drawSectionTitle(graphics, 39,
                    "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode");
            drawSectionTitle(graphics, 81,
                    "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters");
        }
    }

    private void drawSectionBackground(GuiGraphics graphics, int offsetX, int offsetY,
                                       int top, int bottom, String translationKey) {
        DashedSectionRenderer.drawBackground(graphics, font, Component.translatable(translationKey),
                offsetX, offsetY, imageWidth, top, bottom);
    }

    private void drawSectionTitle(GuiGraphics graphics, int y, String translationKey) {
        DashedSectionRenderer.drawTitle(graphics, font, Component.translatable(translationKey), y);
    }

    private void addRedstoneModeButton(String widgetId, RedstoneOutputMode mode) {
        var button = widgets.addButton(widgetId, Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode." + mode.getSerializedName()),
                () -> menu.setRedstoneMode(mode));
        redstoneModeButtons.put(mode, button);
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
