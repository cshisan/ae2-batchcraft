package cn.ae2bc.client;

import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PTunnelPart;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.dispatch.TaskAllocationMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.logic.EnergyDistributionMode;
import appeng.client.gui.widgets.GuiNumberBox;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.Arrays;

/** 1.12.2 input/output configuration screen ported from the 1.21 layouts. */
public final class PatternP2PTunnelScreen extends GuiContainer {
    private static final int PAGE_BUTTON_TOP = 20;
    private static final int PAGE_BUTTON_HEIGHT = 20;
    private static final int PAGE_BUTTON_SPACING = 2;
    private enum Page { COMMON, OUTPUT_COMMON, UNIT_COMMON, TRANSFER, BREAK, REDSTONE, ENERGY }

    private final PatternP2PTunnelMenu menu;
    private final boolean output;
    private boolean outputPageGroup;
    private Page page = Page.COMMON;
    private boolean extractionEnabled;
    private boolean syncInputSettings;
    private OutputSlotSharingMode slotSharingMode;
    private EnergyDistributionMode energyMode;
    private TransferPortOutputMode transferPortOutputMode;
    private TaskAllocationMode taskAllocationMode;
    private ReturnMode returnMode;
    private boolean breakRecovery;
    private RedstoneOutputMode redstoneMode;
    private int extractionInterval;
    private int extractionAmount;
    private int redstoneStrength;
    private int pulseWidth;
    private int pulsePeriod;
    private boolean relayoutInProgress;
    private int relayoutTop;
    private GuiNumberBox intervalField;
    private GuiNumberBox amountField;
    private GuiNumberBox strengthField;
    private GuiNumberBox widthField;
    private GuiNumberBox periodField;

    public PatternP2PTunnelScreen(PatternP2PTunnelMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = 172;
        PatternP2PTunnelPart part = menu.getPart();
        output = part != null && part.isOutput();
        extractionEnabled = part != null && part.isExtractionEnabled();
        syncInputSettings = part == null || part.isSyncInputSettings();
        PatternP2PUnitSettings settings = part == null ? PatternP2PUnitSettings.DEFAULT : part.getUnitSettings();
        slotSharingMode = settings.getOutputSlotSharingMode();
        energyMode = settings.getEnergyDistributionMode();
        transferPortOutputMode = settings.getTransferPortOutputMode();
        taskAllocationMode = part == null ? TaskAllocationMode.ROUND_ROBIN
                : part.getTaskAllocationMode();
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidth = settings.getPulseWidthTicks();
        pulsePeriod = settings.getPulsePeriodTicks();
        ySize = resolveHeight(page);
    }

    @Override public void initGui() {
        ySize = resolveHeight(page);
        super.initGui();
        if (relayoutInProgress) {
            guiTop = relayoutTop;
        } else {
            relayoutTop = guiTop;
        }
        Keyboard.enableRepeatEvents(true);
        buttonList.add(new Ae2Button(1, guiLeft + 148, guiTop - 5, 20, 20, "X"));
        if (!output) {
            buttonList.add(new Ae2IconButton(62, guiLeft + xSize + 2, guiTop + 17,
                    Ae2IconButton.ARROW_RIGHT, 1.0F, -1, -1));
            buttonList.add(new Ae2IconButton(60, guiLeft + xSize + 2, guiTop + 39, Ae2IconButton.ICON_128));
            buttonList.add(new Ae2IconButton(61, guiLeft + xSize + 2, guiTop + 61,
                    Ae2IconButton.FUZZY_PERCENT_99));
        }
        if (output) initOutput(); else initInput();
    }

    private void initInput() {
        buttonList.add(pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD));
        buttonList.add(pageIconButton(3, outputPageGroup ? Page.UNIT_COMMON : Page.OUTPUT_COMMON,
                42, Ae2IconButton.PERMISSION_BUILD_DISABLED));
        if (outputPageGroup) buttonList.add(pageIconButton(
                4, nextUnitPortPage(), 64, Ae2IconButton.ARROW_RIGHT, -1, -1));
        if (page == Page.COMMON) {
            buttonList.add(modeButton(10, 12, ReturnMode.STRICT, true));
            buttonList.add(modeButton(11, 91, ReturnMode.UNBLOCKED, true));
            intervalField = new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 96, 36,
                    fontRenderer.FONT_HEIGHT, Integer.class);
            intervalField.setEnableBackgroundDrawing(true);
            intervalField.setText(Integer.toString(extractionInterval));
            intervalField.setMaxStringLength(16);
            intervalField.setTextColor(0xFFFFFF);
            intervalField.setVisible(true);
            amountField = new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 117, 36,
                    fontRenderer.FONT_HEIGHT, Integer.class);
            amountField.setEnableBackgroundDrawing(true);
            amountField.setText(Integer.toString(extractionAmount));
            amountField.setMaxStringLength(16);
            amountField.setTextColor(0xFFFFFF);
            amountField.setVisible(true);
        } else if (page == Page.OUTPUT_COMMON) {
            GuiButton enabled = new Ae2Button(12, guiLeft + 12, guiTop + 50, 71, 20, tr("gui.ae2_batchcraft.enabled"));
            GuiButton disabled = new Ae2Button(13, guiLeft + 89, guiTop + 50, 71, 20, tr("gui.ae2_batchcraft.disabled"));
            enabled.enabled = !extractionEnabled;
            disabled.enabled = extractionEnabled;
            buttonList.add(enabled);
            buttonList.add(disabled);
        } else if (page == Page.UNIT_COMMON) {
            GuiButton all = new ScrollingAe2Button(15, guiLeft + 12, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.all"));
            GuiButton disabled = new ScrollingAe2Button(16, guiLeft + 63, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.disabled"));
            GuiButton follow = new ScrollingAe2Button(17, guiLeft + 114, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.follow_port"));
            all.enabled = slotSharingMode != OutputSlotSharingMode.ALL;
            disabled.enabled = slotSharingMode != OutputSlotSharingMode.DISABLED;
            follow.enabled = slotSharingMode != OutputSlotSharingMode.FOLLOW_PORT;
            buttonList.add(all); buttonList.add(disabled); buttonList.add(follow);
        } else if (page == Page.TRANSFER) {
            for (TransferPortOutputMode mode : TransferPortOutputMode.values()) {
                buttonList.add(transferModeButton(25 + mode.getId(), 12 + mode.getId() * 52, mode, true));
            }
        } else if (page == Page.BREAK) {
            String label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
            buttonList.add(new ToggleSwitch(20, guiLeft + 12, guiTop + 53,
                    fontRenderer.getStringWidth(label) + ToggleSwitch.LABEL_OFFSET,
                    label, breakRecovery));
        } else if (page == Page.REDSTONE) {
            buttonList.add(redstoneButton(30, 12, RedstoneOutputMode.SINGLE_TRIGGER));
            buttonList.add(redstoneButton(31, 64, RedstoneOutputMode.PERIODIC_PULSE));
            buttonList.add(redstoneButton(32, 116, RedstoneOutputMode.CONTINUOUS));
            strengthField = new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 96, 36,
                    fontRenderer.FONT_HEIGHT, Integer.class);
            strengthField.setEnableBackgroundDrawing(true);
            strengthField.setText(Integer.toString(redstoneStrength));
            strengthField.setMaxStringLength(16);
            strengthField.setTextColor(0xFFFFFF);
            strengthField.setVisible(true);
            widthField = new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 117, 36,
                    fontRenderer.FONT_HEIGHT, Integer.class);
            widthField.setEnableBackgroundDrawing(true);
            widthField.setText(Integer.toString(pulseWidth));
            widthField.setMaxStringLength(16);
            widthField.setTextColor(0xFFFFFF);
            widthField.setVisible(true);
            periodField = new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 138, 36,
                    fontRenderer.FONT_HEIGHT, Integer.class);
            periodField.setEnableBackgroundDrawing(true);
            periodField.setText(Integer.toString(pulsePeriod));
            periodField.setMaxStringLength(16);
            periodField.setTextColor(0xFFFFFF);
            periodField.setVisible(true);
        } else if (page == Page.ENERGY) {
            buttonList.add(new Ae2Button(50, guiLeft + 12, guiTop + 50, 152, 20,
                    tr("gui.ae2_batchcraft.energy_distribution_mode." + energyMode.getSerializedName())));
        }
    }

    private void initOutput() {
        buttonList.add(pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD));
        buttonList.add(pageIconButton(3, Page.OUTPUT_COMMON, 42,
                Ae2IconButton.PERMISSION_BUILD_DISABLED));
        if (page == Page.COMMON) {
            String syncLabel = tr("gui.ae2_batchcraft.sync_input_settings");
            buttonList.add(new ToggleSwitch(40, guiLeft + xSize - 8 - fontRenderer.getStringWidth(syncLabel) - ToggleSwitch.LABEL_OFFSET, guiTop + 23,
                    fontRenderer.getStringWidth(syncLabel) + ToggleSwitch.LABEL_OFFSET,
                    syncLabel, syncInputSettings));
            buttonList.add(modeButton(41, 12, ReturnMode.STRICT, !syncInputSettings));
            buttonList.add(modeButton(42, 91, ReturnMode.UNBLOCKED, !syncInputSettings));
            intervalField = numberField(guiLeft + 104, guiTop + 96, extractionInterval);
            amountField = numberField(guiLeft + 104, guiTop + 117, extractionAmount);
            intervalField.setEnabled(!syncInputSettings);
            amountField.setEnabled(!syncInputSettings);
        } else {
            GuiButton enabled = new Ae2Button(44, guiLeft + 12, guiTop + 50, 71, 20, tr("gui.ae2_batchcraft.enabled"));
            GuiButton disabled = new Ae2Button(45, guiLeft + 89, guiTop + 50, 71, 20, tr("gui.ae2_batchcraft.disabled"));
            enabled.enabled = !syncInputSettings && !extractionEnabled;
            disabled.enabled = !syncInputSettings && extractionEnabled;
            buttonList.add(enabled); buttonList.add(disabled);
        }
        buttonList.add(new Ae2IconButton(60, guiLeft + xSize + 2, guiTop + 17, Ae2IconButton.ICON_128));
    }

    private GuiButton modeButton(int id, int x, ReturnMode mode, boolean editable) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 73, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + mode.getSerializedName()));
        button.enabled = editable && returnMode != mode;
        return button;
    }

    private GuiNumberBox numberField(int x, int y, int value) {
        GuiNumberBox field = new GuiNumberBox(fontRenderer, x, y, 36,
                fontRenderer.FONT_HEIGHT, Integer.class);
        field.setEnableBackgroundDrawing(true);
        field.setText(Integer.toString(value));
        field.setMaxStringLength(16);
        field.setTextColor(0xFFFFFF);
        field.setVisible(true);
        return field;
    }

    private GuiButton redstoneButton(int id, int x, RedstoneOutputMode mode) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode." + mode.getSerializedName()));
        button.enabled = redstoneMode != mode;
        return button;
    }

    private GuiButton transferModeButton(int id, int x, TransferPortOutputMode mode, boolean editable) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName()));
        button.enabled = editable && transferPortOutputMode != mode;
        return button;
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.player.closeScreen();
        else if (button.id >= 2 && button.id <= 4) {
            page = button.id == 2 ? Page.COMMON : button.id == 3
                    ? (outputPageGroup ? Page.UNIT_COMMON : Page.OUTPUT_COMMON)
                    : nextUnitPortPage();
            rebuildGui();
        }
        else if (button.id == 10 || button.id == 41) {
            returnMode = ReturnMode.STRICT;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 11 || button.id == 42) {
            returnMode = ReturnMode.UNBLOCKED;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 12 || button.id == 13 || button.id == 44 || button.id == 45) {
            extractionEnabled = button.id == 12 || button.id == 44;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 60 || button.id == 43) {
            captureFields();
            TaskResetConfirmation.open(this,
                    output ? "gui.ae2_batchcraft.reset_task.confirm.output"
                            : "gui.ae2_batchcraft.reset_task.confirm.input",
                    () -> sendCurrentSettings(true));
        }
        else if (button.id == 61) {
            taskAllocationMode = taskAllocationMode.next();
            ModNetwork.sendTaskAllocationMode(menu, taskAllocationMode);
        }
        else if (button.id >= 15 && button.id <= 17) {
            slotSharingMode = button.id == 15 ? OutputSlotSharingMode.ALL
                    : button.id == 16 ? OutputSlotSharingMode.DISABLED : OutputSlotSharingMode.FOLLOW_PORT;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id >= 25 && button.id <= 27) {
            transferPortOutputMode = TransferPortOutputMode.fromId(button.id - 25);
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 20) {
            breakRecovery = ((ToggleSwitch) button).selected();
            sendCurrentSettings(false);
        }
        else if (button.id >= 30 && button.id <= 32) {
            captureFields();
            redstoneMode = button.id == 30 ? RedstoneOutputMode.SINGLE_TRIGGER
                    : button.id == 31 ? RedstoneOutputMode.PERIODIC_PULSE : RedstoneOutputMode.CONTINUOUS;
            sendCurrentSettings(false);
            rebuildGui();
        } else if (button.id == 40) {
            syncInputSettings = ((ToggleSwitch) button).selected();
            sendCurrentSettings(false);
            rebuildGui();
        } else if (button.id == 50) {
            energyMode = energyMode.next();
            sendCurrentSettings(false);
            rebuildGui();
        } else if (button.id == 62) {
            outputPageGroup = !outputPageGroup;
            page = Page.COMMON;
            rebuildGui();
        }
    }

    private void switchPage() {
        captureFields();
        page = page == Page.COMMON ? Page.TRANSFER : page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE : Page.COMMON;
        rebuildGui();
    }

    private GuiButton pageIconButton(int id, Page target, int y, int iconIndex) {
        return pageIconButton(id, target, y, iconIndex, 1.0F);
    }

    private GuiButton pageIconButton(int id, Page target, int y, int iconIndex,
                                     int iconOffsetX, int iconOffsetY) {
        return pageIconButton(id, target, y, iconIndex, 1.0F, iconOffsetX, iconOffsetY);
    }

    private GuiButton pageIconButton(int id, Page target, int y, int iconIndex, float iconScale) {
        return pageIconButton(id, target, y, iconIndex, iconScale, 0, 0);
    }

    private GuiButton pageIconButton(int id, Page target, int y, int iconIndex, float iconScale,
                                     int iconOffsetX, int iconOffsetY) {
        Ae2IconButton button = new Ae2IconButton(id, guiLeft - 24, guiTop + y, iconIndex, iconScale,
                iconOffsetX, iconOffsetY);
        button.enabled = page != target;
        return button;
    }

    private Page nextUnitPortPage() {
        return page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE
                : page == Page.REDSTONE ? Page.ENERGY : Page.TRANSFER;
    }

    private void rebuildGui() {
        buttonList.clear();
        relayoutTop = guiTop;
        relayoutInProgress = true;
        try {
            initGui();
        } finally {
            relayoutInProgress = false;
        }
    }

    private void captureFields() {
        if (intervalField != null) extractionInterval = parse(intervalField.getText(), extractionInterval);
        if (amountField != null) extractionAmount = parse(amountField.getText(), extractionAmount);
        if (strengthField != null) redstoneStrength = parse(strengthField.getText(), redstoneStrength);
        if (widthField != null) pulseWidth = parse(widthField.getText(), pulseWidth);
        if (periodField != null) pulsePeriod = parse(periodField.getText(), pulsePeriod);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (page == Page.COMMON && (intervalField.textboxKeyTyped(typedChar, keyCode)
                || amountField.textboxKeyTyped(typedChar, keyCode))) {
            sendCurrentSettings(false);
            return;
        }
        if (!output && page == Page.REDSTONE && (strengthField.textboxKeyTyped(typedChar, keyCode)
                || widthField.textboxKeyTyped(typedChar, keyCode) || periodField.textboxKeyTyped(typedChar, keyCode))) {
            sendCurrentSettings(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        super.mouseClicked(x, y, button);
        if (page == Page.COMMON) { intervalField.mouseClicked(x, y, button); amountField.mouseClicked(x, y, button); }
        else if (!output && page == Page.REDSTONE) {
            strengthField.mouseClicked(x, y, button); widthField.mouseClicked(x, y, button); periodField.mouseClicked(x, y, button);
        }
    }

    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void sendCurrentSettings(boolean resetTask) {
        captureFields();
        int period = Math.max(1, pulsePeriod);
        PatternP2PUnitSettings settings = new PatternP2PUnitSettings(returnMode, breakRecovery,
                ProductExtractionLimits.clampInterval(extractionInterval),
                ProductExtractionLimits.clampAmount(extractionAmount), redstoneMode,
                Math.max(0, Math.min(15, redstoneStrength)),
                Math.max(1, Math.min(period, pulseWidth)), period,
                transferPortOutputMode, slotSharingMode, energyMode);
        ModNetwork.sendPattern(menu, extractionEnabled,
                settings, syncInputSettings, resetTask);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        Ae2GuiSkin.draw(guiLeft, guiTop, xSize, ySize);
        if (page == Page.COMMON) {
            section("gui.ae2_batchcraft.return_configuration", 43, 74);
            section("gui.ae2_batchcraft.product_extraction.parameters", 85, 137);
        } else if (page == Page.OUTPUT_COMMON) {
            section("gui.ae2_batchcraft.product_extraction.title", 43, 74);
        } else if (page == Page.UNIT_COMMON) {
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 43, 74);
        } else if (page == Page.TRANSFER) section("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 43, 74);
        else if (page == Page.BREAK) section("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 43, 74);
        else if (page == Page.REDSTONE) {
            section("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 43, 74);
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 85, 158);
        } else {
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 43, 74);
        }
        if (!output && page == Page.COMMON) {
        } else if (!output && page == Page.REDSTONE) {
        }
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tr(output ? "item.ae2_batchcraft.pattern_p2p_tunnel_output.name"
                : "item.ae2_batchcraft.pattern_p2p_tunnel_input.name"), 8, 6, 0x404040);
        fontRenderer.drawString(tr("gui.ae2_batchcraft.pattern_p2p_unit.page." + page.name().toLowerCase()), 8, 23, 0x404040);
        if (page == Page.COMMON) {
            title("gui.ae2_batchcraft.return_configuration", 39); title("gui.ae2_batchcraft.product_extraction.parameters", 81);
            value("gui.ae2_batchcraft.product_extraction.interval", 96, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.product_extraction.amount", 117, "gui.ae2_batchcraft.product_extraction.unit");
        } else if (page == Page.OUTPUT_COMMON) title("gui.ae2_batchcraft.product_extraction.title", 39);
        else if (page == Page.UNIT_COMMON) title("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 39);
        else if (page == Page.TRANSFER) title("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 39);
        else if (page == Page.BREAK) title("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
        else if (page == Page.REDSTONE) {
            title("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 39); title("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 81);
            value("gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength", 96, null);
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_width", 117, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_period", 138, "gui.ae2_batchcraft.time.ticks");
        } else {
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 39);
        }
    }

    private void section(String key, int top, int bottom) { DashedSectionRenderer.draw(fontRenderer, tr(key), guiLeft, guiTop, xSize, top, bottom); }
    private void section(String key, int trailingReservedWidth, int top, int bottom) {
        DashedSectionRenderer.draw(fontRenderer, tr(key), trailingReservedWidth,
                guiLeft, guiTop, xSize, top, bottom);
    }
    private void title(String key, int y) { DashedSectionRenderer.title(fontRenderer, tr(key), y); }
    private void value(String key, int y, String unit) { fontRenderer.drawString(tr(key), 12, y, 0x404040); if (unit != null) fontRenderer.drawString(tr(unit), 143, y, 0x707070); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (page == Page.COMMON) {
            intervalField.drawTextBox();
            amountField.drawTextBox();
        } else if (!output && page == Page.REDSTONE) {
            strengthField.drawTextBox();
            widthField.drawTextBox();
            periodField.drawTextBox();
        }
        renderHoveredToolTip(mouseX, mouseY);
        drawControlTooltip(mouseX, mouseY);
    }

    private void drawControlTooltip(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (!isHovered(button, mouseX, mouseY)) continue;
            String key = null;
            if (button.id == 60 || button.id == 43) {
                key = "gui.ae2_batchcraft.reset_task.tooltip";
            } else if (button.id == 62) {
                String target = tr("gui.ae2_batchcraft.pattern_p2p_unit.page_group."
                        + (outputPageGroup ? "output" : "unit"));
                drawHoveringText(Collections.singletonList(tr(
                        "gui.ae2_batchcraft.pattern_p2p_unit.page_group.switch", target)), mouseX, mouseY);
                return;
            } else if (button.id >= 2 && button.id <= 4) {
                Page target = button.id == 2 ? Page.COMMON : button.id == 3
                        ? (outputPageGroup ? Page.UNIT_COMMON : Page.OUTPUT_COMMON)
                        : nextUnitPortPage();
                String name = tr("gui.ae2_batchcraft.pattern_p2p_unit.page."
                        + target.name().toLowerCase(java.util.Locale.ROOT));
                String tooltip = button.id == 4
                        ? tr("gui.ae2_batchcraft.pattern_p2p_unit.page.switch", name) : name;
                drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
                return;
            } else if (button.id == 12 || button.id == 13 || button.id == 44 || button.id == 45) {
                key = "gui.ae2_batchcraft.product_extraction.enabled.tooltip";
            } else if (button.id == 61) {
                TaskAllocationMode nextMode = taskAllocationMode.next();
                String tooltip = tr("gui.ae2_batchcraft.task_allocation_mode.switch.tooltip",
                        allocationModeName(taskAllocationMode),
                        tr("gui.ae2_batchcraft.task_allocation_mode."
                                + taskAllocationMode.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip"),
                        allocationModeName(nextMode));
                tooltip = tooltip.replace("\\n", "\n");
                drawHoveringText(Arrays.asList(tooltip.split("\\n", -1)), mouseX, mouseY);
                return;
            } else if (button.id >= 15 && button.id <= 17) {
                key = "gui.ae2_batchcraft.pattern_p2p_unit.single_slot.tooltip";
            } else if (button.id >= 25 && button.id <= 27) {
                TransferPortOutputMode mode = TransferPortOutputMode.fromId(button.id - 25);
                key = "gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode."
                        + mode.getSerializedName() + ".tooltip";
            }
            if (key != null) drawHoveringText(Collections.singletonList(tr(key)), mouseX, mouseY);
            return;
        }
    }

    private static boolean isHovered(GuiButton button, int mouseX, int mouseY) {
        return button.visible && mouseX >= button.x && mouseY >= button.y
                && mouseX < button.x + button.width && mouseY < button.y + button.height;
    }

    private static int heightFor(Page page) { return page == Page.COMMON ? 146 : page == Page.REDSTONE ? 166 : 82; }
    private static int resolveHeight(Page page) {
        int pageCount = 3;
        int toolbarHeight = PAGE_BUTTON_TOP + pageCount * PAGE_BUTTON_HEIGHT
                + Math.max(0, pageCount - 1) * PAGE_BUTTON_SPACING;
        return Math.max(heightFor(page), toolbarHeight);
    }
    private static int parse(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
    private static String check(String key, boolean value) { return (value ? "[x] " : "[ ] ") + tr(key); }
    private static String tr(String key) { return new TextComponentTranslation(key).getUnformattedText(); }
    private static String tr(String key, Object... args) {
        return new TextComponentTranslation(key, args).getUnformattedText();
    }
    private static String allocationModeName(TaskAllocationMode mode) {
        return tr("gui.ae2_batchcraft.task_allocation_mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

}
