package cn.ae2bc.client;

import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PUnitManagerPart;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.logic.EnergyDistributionMode;
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

/** Paged 1.12.2 implementation of the 1.21 unit-manager screen. */
public final class PatternP2PUnitManagerScreen extends GuiContainer {
    private static final int PAGE_BUTTON_TOP = 20;
    private static final int PAGE_BUTTON_HEIGHT = 20;
    private static final int PAGE_BUTTON_SPACING = 2;
    private enum Page { COMMON, UNIT_COMMON, TRANSFER, BREAK, REDSTONE, ENERGY }

    private final PatternP2PUnitManagerMenu menu;
    private Page page = Page.COMMON;
    private boolean syncMain;
    private OutputSlotSharingMode slotSharingMode;
    private EnergyDistributionMode energyMode;
    private TransferPortOutputMode transferPortOutputMode;
    private ReturnMode returnMode;
    private boolean breakRecovery;
    private RedstoneOutputMode redstoneMode;
    private int extractionInterval;
    private int extractionAmount;
    private int redstoneStrength;
    private int pulseWidth;
    private int pulsePeriod;
    private GuiNumberBox intervalField;
    private GuiNumberBox amountField;
    private GuiNumberBox strengthField;
    private GuiNumberBox widthField;
    private GuiNumberBox periodField;
    private boolean relayoutInProgress;
    private int relayoutTop;

    public PatternP2PUnitManagerScreen(PatternP2PUnitManagerMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = 172;
        PatternP2PUnitManagerPart part = menu.getPart();
        PatternP2PUnitSettings settings = part == null ? PatternP2PUnitSettings.DEFAULT : part.getSettings();
        syncMain = part == null || part.isSyncMainConfiguration();
        slotSharingMode = settings.getOutputSlotSharingMode();
        energyMode = part == null ? EnergyDistributionMode.EVEN : part.getEnergyDistributionMode();
        transferPortOutputMode = settings.getTransferPortOutputMode();
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
        buttonList.add(pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD));
        buttonList.add(pageIconButton(3, Page.UNIT_COMMON, 42,
                Ae2IconButton.PERMISSION_BUILD_DISABLED));
        buttonList.add(pageIconButton(4, nextPortPage(), 64, Ae2IconButton.ARROW_RIGHT, -1, -1));
        buttonList.add(new Ae2IconButton(14, guiLeft + xSize + 2, guiTop + 17, Ae2IconButton.ICON_128));
        if (page == Page.COMMON) initCommon();
        else if (page == Page.UNIT_COMMON) initUnitCommon();
        else if (page == Page.TRANSFER) initTransfer();
        else if (page == Page.BREAK) initBreak();
        else if (page == Page.REDSTONE) initRedstone();
        else initEnergy();
    }

    private void initCommon() {
        String syncLabel = tr("gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration");
        int syncWidth = fontRenderer.getStringWidth(syncLabel) + ToggleSwitch.LABEL_OFFSET;
        buttonList.add(new ToggleSwitch(10,
                guiLeft + xSize - 8 - syncWidth, guiTop + 22, syncWidth, syncLabel, syncMain));
        buttonList.add(modeButton(11, 12, ReturnMode.STRICT));
        buttonList.add(modeButton(12, 91, ReturnMode.UNBLOCKED));
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
        intervalField.setEnabled(!syncMain);
        amountField.setEnabled(!syncMain);
    }

    private void initUnitCommon() {
        GuiButton all = new ScrollingAe2Button(15, guiLeft + 12, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.all"));
        GuiButton disabled = new ScrollingAe2Button(16, guiLeft + 63, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.disabled"));
        GuiButton follow = new ScrollingAe2Button(17, guiLeft + 114, guiTop + 50, 46, 20, tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.follow_port"));
        all.enabled = !syncMain && slotSharingMode != OutputSlotSharingMode.ALL;
        disabled.enabled = !syncMain && slotSharingMode != OutputSlotSharingMode.DISABLED;
        follow.enabled = !syncMain && slotSharingMode != OutputSlotSharingMode.FOLLOW_PORT;
        buttonList.add(all); buttonList.add(disabled); buttonList.add(follow);
    }

    private void initTransfer() {
        for (TransferPortOutputMode mode : TransferPortOutputMode.values()) {
            buttonList.add(transferModeButton(25 + mode.getId(), 12 + mode.getId() * 52, mode));
        }
    }

    private void initBreak() {
        String label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
        ToggleSwitch button = new ToggleSwitch(20, guiLeft + 12, guiTop + 53,
                fontRenderer.getStringWidth(label) + ToggleSwitch.LABEL_OFFSET,
                label, breakRecovery);
        button.enabled = !syncMain;
        buttonList.add(button);
    }

    private void initRedstone() {
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
        strengthField.setEnabled(!syncMain);
        widthField.setEnabled(!syncMain);
        periodField.setEnabled(!syncMain);
    }

    private void initEnergy() {
        GuiButton button = new Ae2Button(40, guiLeft + 12, guiTop + 50, 152, 20,
                tr("gui.ae2_batchcraft.energy_distribution_mode." + energyMode.getSerializedName()));
        button.enabled = !syncMain;
        buttonList.add(button);
    }

    private GuiButton modeButton(int id, int x, ReturnMode mode) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 73, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + mode.getSerializedName()));
        button.enabled = !syncMain && returnMode != mode;
        return button;
    }

    private GuiButton redstoneButton(int id, int x, RedstoneOutputMode mode) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode." + mode.getSerializedName()));
        button.enabled = !syncMain && redstoneMode != mode;
        return button;
    }

    private GuiButton transferModeButton(int id, int x, TransferPortOutputMode mode) {
        GuiButton button = new Ae2Button(id, guiLeft + x, guiTop + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName()));
        button.enabled = !syncMain && transferPortOutputMode != mode;
        return button;
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.player.closeScreen();
        else if (button.id >= 2 && button.id <= 4) {
            Page target = button.id == 4 ? nextPortPage() : pageForButtonId(button.id);
            if (target == null) return;
            captureFields();
            page = target;
            rebuildGui();
        }
        else if (button.id == 10) {
            syncMain = ((ToggleSwitch) button).selected();
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 11) {
            returnMode = ReturnMode.STRICT;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 12) {
            returnMode = ReturnMode.UNBLOCKED;
            sendCurrentSettings(false);
            rebuildGui();
        }
        else if (button.id == 14) {
            captureFields();
            TaskResetConfirmation.open(this, "gui.ae2_batchcraft.reset_task.confirm.unit",
                    () -> sendCurrentSettings(true));
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
        else if (button.id == 40) {
            energyMode = energyMode.next();
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
        }
    }

    private void switchPage() {
        captureFields();
        page = page == Page.COMMON ? Page.UNIT_COMMON : page == Page.UNIT_COMMON ? Page.TRANSFER : page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE : page == Page.REDSTONE ? Page.ENERGY : Page.COMMON;
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

    private static Page pageForButtonId(int id) {
        switch (id) {
            case 2: return Page.COMMON;
            case 3: return Page.UNIT_COMMON;
            default: return null;
        }
    }

    private Page nextPortPage() {
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
        if (page == Page.REDSTONE && (strengthField.textboxKeyTyped(typedChar, keyCode)
                || widthField.textboxKeyTyped(typedChar, keyCode)
                || periodField.textboxKeyTyped(typedChar, keyCode))) {
            sendCurrentSettings(false);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        super.mouseClicked(x, y, button);
        if (page == Page.COMMON) { intervalField.mouseClicked(x, y, button); amountField.mouseClicked(x, y, button); }
        else if (page == Page.REDSTONE) {
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
                Math.max(1, Math.min(period, pulseWidth)), period);
        settings = new PatternP2PUnitSettings(settings.getReturnMode(), settings.isBreakRecovery(),
                settings.getExtractionInterval(), settings.getExtractionAmount(), settings.getRedstoneMode(),
                settings.getRedstoneStrength(), settings.getPulseWidthTicks(), settings.getPulsePeriodTicks(),
                transferPortOutputMode, slotSharingMode, energyMode);
        PatternP2PUnitManagerPart part = menu.getPart();
        ModNetwork.sendUnitManagerSettings(menu, part == null ? 0 : part.getFrequencyUnsigned(),
                settings, syncMain, energyMode, resetTask);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        Ae2GuiSkin.draw(guiLeft, guiTop, xSize, ySize);
        if (page == Page.COMMON) {
            section("gui.ae2_batchcraft.return_configuration", 43, 74);
            section("gui.ae2_batchcraft.product_extraction.parameters", 85, 137);
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
        if (page == Page.COMMON) {
        } else if (page == Page.REDSTONE) {
        }
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tr("item.ae2_batchcraft.pattern_p2p_unit_manager.name"), 8, 6, 0x404040);
        fontRenderer.drawString(tr("gui.ae2_batchcraft.pattern_p2p_unit.page." + page.name().toLowerCase()), 8, 23, 0x404040);
        if (page == Page.COMMON) {
            title("gui.ae2_batchcraft.return_configuration", 39);
            title("gui.ae2_batchcraft.product_extraction.parameters", 81);
            value("gui.ae2_batchcraft.product_extraction.interval", 96, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.product_extraction.amount", 117, "gui.ae2_batchcraft.product_extraction.unit");
        } else if (page == Page.UNIT_COMMON) {
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 39);
        } else if (page == Page.TRANSFER) title("gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 39);
        else if (page == Page.BREAK) title("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
        else if (page == Page.REDSTONE) {
            title("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 39);
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 81);
            value("gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength", 96, null);
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_width", 117, "gui.ae2_batchcraft.time.ticks");
            value("gui.ae2_batchcraft.pattern_p2p_unit.pulse_period", 138, "gui.ae2_batchcraft.time.ticks");
        } else {
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 39);
        }
    }

    private void section(String key, int top, int bottom) {
        DashedSectionRenderer.draw(fontRenderer, tr(key), guiLeft, guiTop, xSize, top, bottom);
    }
    private void title(String key, int y) { DashedSectionRenderer.title(fontRenderer, tr(key), y); }
    private void value(String key, int y, String unit) {
        fontRenderer.drawString(tr(key), 12, y, 0x404040);
        if (unit != null) fontRenderer.drawString(tr(unit), 143, y, 0x707070);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (page == Page.COMMON) {
            intervalField.drawTextBox();
            amountField.drawTextBox();
        } else if (page == Page.REDSTONE) {
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
            if (button.id == 14) {
                key = "gui.ae2_batchcraft.reset_task.tooltip.unit";
            } else if (button.id >= 2 && button.id <= 4) {
                Page target = button.id == 2 ? Page.COMMON
                        : button.id == 3 ? Page.UNIT_COMMON : nextPortPage();
                String name = tr("gui.ae2_batchcraft.pattern_p2p_unit.page."
                        + target.name().toLowerCase(java.util.Locale.ROOT));
                String tooltip = button.id == 4
                        ? tr("gui.ae2_batchcraft.pattern_p2p_unit.page.switch", name) : name;
                drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY);
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
    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }
    private static String check(String key, boolean value) { return (value ? "[x] " : "[ ] ") + tr(key); }
    private static String tr(String key) { return new TextComponentTranslation(key).getUnformattedText(); }
    private static String tr(String key, Object... args) {
        return new TextComponentTranslation(key, args).getUnformattedText();
    }

}
