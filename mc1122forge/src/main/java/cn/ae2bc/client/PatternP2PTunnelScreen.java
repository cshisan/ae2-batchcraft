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
    private enum Page { COMMON, TRANSFER, BREAK, REDSTONE }

    private final PatternP2PTunnelMenu menu;
    private final boolean output;
    private Page page = Page.COMMON;
    private boolean extractionEnabled;
    private boolean syncInputSettings;
    private OutputSlotSharingMode slotSharingMode;
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
        xSize = 176;
        PatternP2PTunnelPart part = menu.getPart();
        output = part != null && part.isOutput();
        extractionEnabled = part != null && part.isExtractionEnabled();
        syncInputSettings = part == null || part.isSyncInputSettings();
        PatternP2PUnitSettings settings = part == null ? PatternP2PUnitSettings.DEFAULT : part.getUnitSettings();
        slotSharingMode = settings.getOutputSlotSharingMode();
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
        ySize = output ? 124 : resolveHeight(page);
    }

    @Override public void initGui() {
        ySize = output ? 124 : resolveHeight(page);
        super.initGui();
        if (relayoutInProgress) {
            guiTop = relayoutTop;
        } else {
            relayoutTop = guiTop;
        }
        Keyboard.enableRepeatEvents(true);
        buttonList.add(new Ae2Button(1, guiLeft + 152, guiTop - 5, 20, 20, "X"));
        if (!output) {
            buttonList.add(new Ae2IconButton(6, guiLeft + xSize + 2, guiTop + 17, Ae2IconButton.ICON_128));
            buttonList.add(new Ae2IconButton(7, guiLeft + xSize + 2, guiTop + 39,
                    Ae2IconButton.FUZZY_PERCENT_99));
        }
        if (output) initOutput(); else initInput();
    }

    private void initInput() {
        buttonList.add(pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD));
        buttonList.add(pageIconButton(3, Page.TRANSFER, 42, Ae2IconButton.FULLNESS_HALF));
        buttonList.add(pageIconButton(4, Page.BREAK, 64, Ae2IconButton.PERMISSION_CRAFT));
        buttonList.add(pageIconButton(5, Page.REDSTONE, 86, 1));
        if (page == Page.COMMON) {
            buttonList.add(modeButton(10, 12, ReturnMode.STRICT, true));
            buttonList.add(modeButton(11, 91, ReturnMode.UNBLOCKED, true));
            int extractionWidth = 22;
            buttonList.add(new ToggleSwitch(12,
                    guiLeft + DashedSectionRenderer.trailingContentX(xSize, extractionWidth), guiTop + 77,
                    extractionWidth, "", extractionEnabled));
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
            buttonList.add(new Ae2Button(15, guiLeft + 12, guiTop + 153, 152, 20,
                    tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot."
                            + slotSharingMode.getSerializedName())));
        } else if (page == Page.TRANSFER) {
            for (TransferPortOutputMode mode : TransferPortOutputMode.values()) {
                buttonList.add(transferModeButton(25 + mode.getId(), 12 + mode.getId() * 52, mode, true));
            }
        } else if (page == Page.BREAK) {
            String label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
            buttonList.add(new ToggleSwitch(20, guiLeft + 12, guiTop + 53,
                    fontRenderer.getStringWidth(label) + ToggleSwitch.LABEL_OFFSET,
                    label, breakRecovery));
        } else {
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
        }
    }

    private void initOutput() {
        String syncLabel = tr("gui.ae2_batchcraft.sync_input_settings");
        buttonList.add(new ToggleSwitch(40, guiLeft + 8, guiTop + 23,
                fontRenderer.getStringWidth(syncLabel) + ToggleSwitch.LABEL_OFFSET,
                syncLabel, syncInputSettings));
        buttonList.add(modeButton(41, 12, ReturnMode.STRICT, !syncInputSettings));
        buttonList.add(modeButton(42, 91, ReturnMode.UNBLOCKED, !syncInputSettings));
        buttonList.add(new Ae2Button(43, guiLeft + 12, guiTop + 92, 152, 20,
                tr("gui.ae2_batchcraft.reset_task")));
    }

    private GuiButton modeButton(int id, int x, ReturnMode mode, boolean editable) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 73, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + mode.getSerializedName()));
        button.enabled = editable && returnMode != mode;
        return button;
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
        else if (button.id >= 2 && button.id <= 5) {
            page = button.id == 2 ? Page.COMMON : button.id == 3 ? Page.BREAK : Page.REDSTONE;
            if (button.id == 3) page = Page.TRANSFER;
            else if (button.id == 4) page = Page.BREAK;
            else if (button.id == 5) page = Page.REDSTONE;
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
        else if (button.id == 12) {
            extractionEnabled = ((ToggleSwitch) button).selected();
            sendCurrentSettings(false);
        }
        else if (button.id == 6 || button.id == 43) {
            captureFields();
            TaskResetConfirmation.open(this,
                    output ? "gui.ae2_batchcraft.reset_task.confirm.output"
                            : "gui.ae2_batchcraft.reset_task.confirm.input",
                    () -> sendCurrentSettings(true));
        }
        else if (button.id == 7) {
            taskAllocationMode = taskAllocationMode.next();
            ModNetwork.sendTaskAllocationMode(menu, taskAllocationMode);
        }
        else if (button.id == 15) {
            slotSharingMode = slotSharingMode.next();
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
        }
    }

    private void switchPage() {
        captureFields();
        page = page == Page.COMMON ? Page.TRANSFER : page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE : Page.COMMON;
        rebuildGui();
    }

    private GuiButton pageIconButton(int id, Page target, int y, int iconIndex) {
        Ae2IconButton button = new Ae2IconButton(id, guiLeft - 24, guiTop + y, iconIndex);
        button.enabled = page != target;
        return button;
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
        if (!output && page == Page.COMMON && (intervalField.textboxKeyTyped(typedChar, keyCode)
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
        if (!output && page == Page.COMMON) { intervalField.mouseClicked(x, y, button); amountField.mouseClicked(x, y, button); }
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
                transferPortOutputMode, slotSharingMode);
        ModNetwork.sendPattern(menu, extractionEnabled,
                settings, syncInputSettings, resetTask);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        Ae2GuiSkin.draw(guiLeft, guiTop, xSize, ySize);
        if (output) {
            section("gui.ae2_batchcraft.return_mode", 43, 74);
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 85, 116);
        } else if (page == Page.COMMON) {
            section("gui.ae2_batchcraft.return_configuration", 43, 74);
            section("gui.ae2_batchcraft.product_extraction.title", 22, 85, 137);
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 146, 178);
        } else if (page == Page.TRANSFER) section("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 43, 74);
        else if (page == Page.BREAK) section("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 43, 74);
        else {
            section("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 43, 74);
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 85, 158);
        }
        if (!output && page == Page.COMMON) {
        } else if (!output && page == Page.REDSTONE) {
        }
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tr(output ? "item.ae2_batchcraft.pattern_p2p_tunnel_output.name"
                : "item.ae2_batchcraft.pattern_p2p_tunnel_input.name"), 8, 6, 0x404040);
        if (output) { title("gui.ae2_batchcraft.return_mode", 39); title("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 81); return; }
        fontRenderer.drawString(tr("gui.ae2_batchcraft.pattern_p2p_unit.page." + page.name().toLowerCase()), 8, 23, 0x404040);
        if (page == Page.COMMON) {
            title("gui.ae2_batchcraft.return_configuration", 39); title("gui.ae2_batchcraft.product_extraction.title", 81);
            value("gui.ae2_batchcraft.product_extraction.interval", 96, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.product_extraction.amount", 117, "gui.ae2_batchcraft.product_extraction.unit");
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 142);
        } else if (page == Page.TRANSFER) title("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 39);
        else if (page == Page.BREAK) title("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
        else {
            title("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 39); title("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 81);
            value("gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength", 96, null);
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_width", 117, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_period", 138, "gui.ae2_batchcraft.time.ticks");
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
        if (!output && page == Page.COMMON) {
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
            if (button.id == 6 || button.id == 43) {
                key = "gui.ae2_batchcraft.reset_task.tooltip";
            } else if (button.id == 7) {
                TaskAllocationMode nextMode = taskAllocationMode.next();
                String tooltip = tr("gui.ae2_batchcraft.task_allocation_mode.switch.tooltip",
                        allocationModeName(taskAllocationMode),
                        tr("gui.ae2_batchcraft.task_allocation_mode."
                                + taskAllocationMode.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip"),
                        allocationModeName(nextMode));
                tooltip = tooltip.replace("\\n", "\n");
                drawHoveringText(Arrays.asList(tooltip.split("\\n", -1)), mouseX, mouseY);
                return;
            } else if (button.id == 15) {
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

    private static int heightFor(Page page) { return page == Page.COMMON ? 186 : page == Page.TRANSFER ? 82 : page == Page.BREAK ? 82 : 166; }
    private static int resolveHeight(Page page) {
        int pageCount = Page.values().length;
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
