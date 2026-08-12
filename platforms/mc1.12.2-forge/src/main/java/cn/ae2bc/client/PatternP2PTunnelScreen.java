package cn.ae2bc.client;

import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PTunnelPart;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentTranslation;
import org.lwjgl.input.Keyboard;

import java.util.function.IntSupplier;

/** 1.12.2 input/output configuration screen ported from the 1.21 layouts. */
public final class PatternP2PTunnelScreen extends GuiContainer {
    private enum Page { COMMON, BREAK, REDSTONE }

    private final PatternP2PTunnelMenu menu;
    private final boolean output;
    private Page page = Page.COMMON;
    private boolean extractionEnabled;
    private boolean syncInputSettings;
    private ReturnMode returnMode;
    private boolean breakRecovery;
    private RedstoneOutputMode redstoneMode;
    private int extractionInterval;
    private int extractionAmount;
    private int redstoneStrength;
    private int pulseWidth;
    private int pulsePeriod;
    private boolean resetArmed;
    private GuiTextField intervalField;
    private GuiTextField amountField;
    private GuiTextField strengthField;
    private GuiTextField widthField;
    private GuiTextField periodField;

    public PatternP2PTunnelScreen(PatternP2PTunnelMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = 176;
        PatternP2PTunnelPart part = menu.getPart();
        output = part != null && part.isOutput();
        extractionEnabled = part != null && part.isExtractionEnabled();
        syncInputSettings = part == null || part.isSyncInputSettings();
        PatternP2PUnitSettings settings = part == null ? PatternP2PUnitSettings.DEFAULT : part.getUnitSettings();
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidth = settings.getPulseWidthTicks();
        pulsePeriod = settings.getPulsePeriodTicks();
        ySize = output ? 124 : heightFor(page);
    }

    @Override public void initGui() {
        ySize = output ? 124 : heightFor(page);
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        if (output) initOutput(); else initInput();
    }

    private void initInput() {
        buttonList.add(new Ae2Button(1, guiLeft + 152, guiTop - 5, 20, 20, "X"));
        buttonList.add(new Ae2Button(2, guiLeft - 24, guiTop + 20, 20, 20, ">"));
        if (page == Page.COMMON) {
            buttonList.add(modeButton(10, 12, ReturnMode.STRICT, true));
            buttonList.add(modeButton(11, 91, ReturnMode.UNBLOCKED, true));
            int extractionWidth = 22;
            buttonList.add(new ToggleSwitch(12,
                    guiLeft + DashedSectionRenderer.trailingContentX(xSize, extractionWidth), guiTop + 77,
                    extractionWidth, "", extractionEnabled));
            intervalField = field(0, 104, 92, extractionInterval,
                    ProductExtractionLimits.MIN_INTERVAL, () -> ProductExtractionLimits.MAX_INTERVAL);
            amountField = field(1, 104, 113, extractionAmount,
                    ProductExtractionLimits.MIN_AMOUNT, () -> ProductExtractionLimits.MAX_AMOUNT);
            buttonList.add(new Ae2Button(13, guiLeft + 12, guiTop + 154, 152, 20, resetLabel()));
        } else if (page == Page.BREAK) {
            String label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
            buttonList.add(new ToggleSwitch(20, guiLeft + 12, guiTop + 53,
                    fontRenderer.getStringWidth(label) + ToggleSwitch.LABEL_OFFSET,
                    label, breakRecovery));
        } else {
            buttonList.add(redstoneButton(30, 12, RedstoneOutputMode.SINGLE_TRIGGER));
            buttonList.add(redstoneButton(31, 64, RedstoneOutputMode.PERIODIC_PULSE));
            buttonList.add(redstoneButton(32, 116, RedstoneOutputMode.CONTINUOUS));
            strengthField = field(2, 104, 92, redstoneStrength, 0, () -> 15);
            widthField = field(3, 104, 113, pulseWidth, 1, () -> pulsePeriod);
            periodField = field(4, 104, 134, pulsePeriod, 1,
                    () -> PatternP2PUnitSettings.MAX_PULSE_TICKS);
        }
    }

    private void initOutput() {
        String syncLabel = tr("gui.ae2_batchcraft.sync_input_settings");
        buttonList.add(new ToggleSwitch(40, guiLeft + 8, guiTop + 23,
                fontRenderer.getStringWidth(syncLabel) + ToggleSwitch.LABEL_OFFSET,
                syncLabel, syncInputSettings));
        buttonList.add(modeButton(41, 12, ReturnMode.STRICT, !syncInputSettings));
        buttonList.add(modeButton(42, 91, ReturnMode.UNBLOCKED, !syncInputSettings));
        buttonList.add(new Ae2Button(43, guiLeft + 12, guiTop + 92, 152, 20, resetLabel()));
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

    private GuiTextField field(int id, int x, int y, int value, int minimum, IntSupplier maximum) {
        GuiTextField field = new GuiTextField(id, fontRenderer, guiLeft + x, guiTop + y, 36, 16);
        field.setText(Integer.toString(value));
        field.setMaxStringLength(4);
        field.setValidator(text -> validInteger(text, minimum, maximum.getAsInt()));
        return field;
    }

    private static boolean validInteger(String text, int minimum, int maximum) {
        if (text.isEmpty()) return true;
        try {
            int value = Integer.parseInt(text);
            return value >= minimum && value <= maximum;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) mc.player.closeScreen();
        else if (button.id == 2) switchPage();
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
        else if (button.id == 13 || button.id == 43) armOrReset(button);
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
        page = page == Page.COMMON ? Page.BREAK : page == Page.BREAK ? Page.REDSTONE : Page.COMMON;
        rebuildGui();
    }

    private void armOrReset(GuiButton button) {
        if (resetArmed) { resetArmed = false; sendCurrentSettings(true); }
        else resetArmed = true;
        button.displayString = resetLabel();
    }
    private String resetLabel() { return tr(resetArmed ? "gui.ae2_batchcraft.reset_task.confirm_button"
            : "gui.ae2_batchcraft.reset_task"); }

    private void rebuildGui() {
        buttonList.clear();
        initGui();
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
                Math.max(1, Math.min(period, pulseWidth)), period);
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
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 146, 178);
        } else if (page == Page.BREAK) section("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 43, 74);
        else {
            section("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 43, 74);
            section("gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 85, 158);
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
            title("gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 142);
        } else if (page == Page.BREAK) title("gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
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
        if (!output && page == Page.COMMON) { intervalField.drawTextBox(); amountField.drawTextBox(); }
        else if (!output && page == Page.REDSTONE) { strengthField.drawTextBox(); widthField.drawTextBox(); periodField.drawTextBox(); }
        renderHoveredToolTip(mouseX, mouseY);
    }

    private static int heightFor(Page page) { return page == Page.COMMON ? 186 : page == Page.BREAK ? 82 : 166; }
    private static int parse(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
    private static String check(String key, boolean value) { return (value ? "[x] " : "[ ] ") + tr(key); }
    private static String tr(String key) { return new TextComponentTranslation(key).getUnformattedText(); }
}
