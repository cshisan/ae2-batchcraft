package cn.ae2bc.client;

import appeng.client.gui.Icon;
import cn.ae2bc.menu.PatternP2PTunnelMenu;
import cn.ae2bc.network.ModNetwork;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Input and output screens matching the corresponding 1.21 Pattern P2P screens. */
public final class PatternP2PTunnelScreen extends ContainerScreen<PatternP2PTunnelMenu> {
    private static final int PAGE_BUTTON_TOP = 20;
    private static final int PAGE_BUTTON_HEIGHT = 20;
    private static final int PAGE_BUTTON_SPACING = 2;
    private enum Page { COMMON, TRANSFER, BREAK, REDSTONE }

    private Page page = Page.COMMON;
    private final boolean output;
    private final boolean showSingleSlotControl;
    private boolean extractionEnabled;
    private boolean syncInputSettings;
    private TransferPortOutputMode transferPortOutputMode;
    private OutputSlotSharingMode outputSlotSharingMode;
    private ReturnMode returnMode;
    private boolean breakRecovery;
    private RedstoneOutputMode redstoneMode;
    private int extractionInterval;
    private int extractionAmount;
    private int redstoneStrength;
    private int pulseWidth;
    private int pulsePeriod;
    private Button resetTaskButton;
    private TextFieldWidget intervalField;
    private TextFieldWidget amountField;
    private TextFieldWidget strengthField;
    private TextFieldWidget widthField;
    private TextFieldWidget periodField;

    public PatternP2PTunnelScreen(PatternP2PTunnelMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 176;
        output = menu.isOutput();
        showSingleSlotControl = menu.isInputConfiguration();
        extractionEnabled = menu.isExtractionEnabled();
        syncInputSettings = menu.isSyncInputSettings();
        transferPortOutputMode = menu.getSettings().getTransferPortOutputMode();
        outputSlotSharingMode = menu.getSettings().getOutputSlotSharingMode();
        PatternP2PUnitSettings settings = menu.getSettings();
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidth = settings.getPulseWidthTicks();
        pulsePeriod = settings.getPulsePeriodTicks();
        imageHeight = output ? 124 : resolveHeight(page);
    }

    @Override
    protected void init() {
        imageHeight = output ? 124 : resolveHeight(page);
        super.init();
        addButton(new Ae2Button(leftPos + 152, topPos - 5, 20, 20,
                new StringTextComponent("X"), button -> onClose()));
        if (!output) {
            resetTaskButton = addButton(new Ae2IconButton(leftPos + imageWidth + 2, topPos + 17,
                    Icon.INVALID.ordinal(), ignored -> TaskResetConfirmation.open(this,
                            tr("gui.ae2_batchcraft.reset_task.confirm.input"),
                            () -> sendCurrentSettings(true))));
            resetTaskButton.setMessage(tr("gui.ae2_batchcraft.reset_task.tooltip"));
        }
        if (output) initOutput();
        else initInput();
    }

    private void initInput() {
        addPageIconButton(2, Page.COMMON, 20, Icon.PERMISSION_BUILD.ordinal());
        addPageIconButton(3, Page.TRANSFER, 42, Icon.FULLNESS_HALF.ordinal());
        addPageIconButton(4, Page.BREAK, 64, Icon.PERMISSION_CRAFT.ordinal());
        addPageIconButton(5, Page.REDSTONE, 86, 1);
        if (page == Page.COMMON) {
            addButton(modeButton(12, 50, 73, ReturnMode.STRICT, true));
            addButton(modeButton(91, 50, 73, ReturnMode.UNBLOCKED, true));
            int extractionWidth = 22;
            addButton(new ToggleSwitch(leftPos
                    + DashedSectionRenderer.trailingContentX(imageWidth, extractionWidth), topPos + 77,
                    extractionWidth, StringTextComponent.EMPTY, extractionEnabled, selected -> {
                        extractionEnabled = selected;
                        sendCurrentSettings(false);
                    }));
            intervalField = field(104, 92, extractionInterval,
                    ProductExtractionLimits.MIN_INTERVAL, () -> ProductExtractionLimits.MAX_INTERVAL,
                    value -> extractionInterval = value);
            amountField = field(104, 113, extractionAmount,
                    ProductExtractionLimits.MIN_AMOUNT, () -> ProductExtractionLimits.MAX_AMOUNT,
                    value -> extractionAmount = value);
            if (showSingleSlotControl) {
                addButton(new Ae2Button(leftPos + 12, topPos + 153, 152, 20,
                        tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot."
                                + outputSlotSharingMode.getSerializedName()),
                        button -> {
                            outputSlotSharingMode = outputSlotSharingMode.next();
                            ModNetwork.sendInputOutputSlotSharingMode(menu, outputSlotSharingMode);
                            init(minecraft, width, height);
                        }));
            }
        } else if (page == Page.TRANSFER) {
            addButton(transferButton(12, TransferPortOutputMode.NORMAL));
            addButton(transferButton(64, TransferPortOutputMode.SINGLE_ITEM));
            addButton(transferButton(116, TransferPortOutputMode.SAME_TYPE));
        } else if (page == Page.BREAK) {
            ITextComponent label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
            addButton(new ToggleSwitch(leftPos + 12, topPos + 52,
                    font.width(label) + ToggleSwitch.LABEL_OFFSET,
                    label, breakRecovery, selected -> {
                        breakRecovery = selected;
                        sendCurrentSettings(false);
                    }));
        } else {
            addButton(redstoneButton(12, RedstoneOutputMode.SINGLE_TRIGGER));
            addButton(redstoneButton(64, RedstoneOutputMode.PERIODIC_PULSE));
            addButton(redstoneButton(116, RedstoneOutputMode.CONTINUOUS));
            strengthField = field(104, 92, redstoneStrength, 0, () -> 15,
                    value -> redstoneStrength = value);
            widthField = field(104, 113, pulseWidth, 1, () -> pulsePeriod,
                    value -> pulseWidth = value);
            periodField = field(104, 134, pulsePeriod, 1, () -> PatternP2PUnitSettings.MAX_PULSE_TICKS,
                    value -> pulsePeriod = value);
        }
    }

    private void initOutput() {
        ITextComponent syncLabel = tr("gui.ae2_batchcraft.sync_input_settings");
        addButton(new ToggleSwitch(leftPos + 8, topPos + 22,
                font.width(syncLabel) + ToggleSwitch.LABEL_OFFSET,
                syncLabel, syncInputSettings, selected -> {
                syncInputSettings = selected;
                    sendCurrentSettings(false);
                    init(minecraft, width, height);
                }));
        addButton(modeButton(12, 50, 73, ReturnMode.STRICT, !syncInputSettings));
        addButton(modeButton(91, 50, 73, ReturnMode.UNBLOCKED, !syncInputSettings));
        resetTaskButton = addButton(new Ae2Button(leftPos + 12, topPos + 92, 152, 20,
                tr("gui.ae2_batchcraft.reset_task"),
                ignored -> TaskResetConfirmation.open(this,
                        tr("gui.ae2_batchcraft.reset_task.confirm.output"),
                        () -> sendCurrentSettings(true))));
    }

    private Button modeButton(int x, int y, int width, ReturnMode mode, boolean editable) {
        Button button = new Ae2Button(leftPos + x, topPos + y, width, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + mode.getSerializedName()), pressed -> {
                    returnMode = mode;
                    sendCurrentSettings(false);
                    init(minecraft, this.width, this.height);
                });
        button.active = editable && returnMode != mode;
        return button;
    }

    private Button redstoneButton(int x, RedstoneOutputMode mode) {
        Button button = new Ae2Button(leftPos + x, topPos + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode." + mode.getSerializedName()), pressed -> {
                    captureFields();
                    redstoneMode = mode;
                    sendCurrentSettings(false);
                    init(minecraft, width, height);
                });
        button.active = redstoneMode != mode;
        return button;
    }

    private Button transferButton(int x, TransferPortOutputMode mode) {
        Button button = new Ae2Button(leftPos + x, topPos + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName()), pressed -> {
                    transferPortOutputMode = mode;
                    sendCurrentSettings(false);
                    init(minecraft, width, height);
                });
        button.active = transferPortOutputMode != mode;
        return button;
    }

    private TextFieldWidget field(int x, int y, int value, int minimum, IntSupplier maximum,
            IntConsumer changeListener) {
        TextFieldWidget field = new TextFieldWidget(font, leftPos + x, topPos + y,
                36, 16, StringTextComponent.EMPTY);
        field.setValue(Integer.toString(value));
        field.setMaxLength(Math.max(4, Integer.toString(maximum.getAsInt()).length()));
        field.setFilter(text -> validInteger(text, minimum, maximum.getAsInt()));
        field.setResponder(text -> {
            if (!text.isEmpty()) {
                changeListener.accept(parse(text, value));
                sendCurrentSettings(false);
            }
        });
        children.add(field);
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

    private void switchPage() {
        captureFields();
        page = page == Page.COMMON ? Page.TRANSFER : page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE : Page.COMMON;
        init(minecraft, width, height);
    }

    private void addPageButton(int id, Page target, int y, String label) {
        Button button = new Ae2Button(leftPos - 24, topPos + y, 20, 20,
                new StringTextComponent(label), pressed -> {
                    captureFields();
                    page = target;
                    init(minecraft, width, height);
                });
        button.active = page != target;
        addButton(button);
    }

    private void addPageIconButton(int id, Page target, int y, int iconIndex) {
        Button button = new Ae2IconButton(leftPos - 24, topPos + y, iconIndex, pressed -> {
            captureFields();
            page = target;
            init(minecraft, width, height);
        });
        button.active = page != target;
        addButton(button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Route the right toolbar action before ContainerScreen handles outside-panel clicks.
        if (resetTaskButton != null && resetTaskButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void captureFields() {
        if (intervalField != null) extractionInterval = parse(intervalField.getValue(), extractionInterval);
        if (amountField != null) extractionAmount = parse(amountField.getValue(), extractionAmount);
        if (strengthField != null) redstoneStrength = parse(strengthField.getValue(), redstoneStrength);
        if (widthField != null) pulseWidth = parse(widthField.getValue(), pulseWidth);
        if (periodField != null) pulsePeriod = parse(periodField.getValue(), pulsePeriod);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private void sendCurrentSettings(boolean resetTask) {
        captureFields();
        int period = Math.max(1, pulsePeriod);
        PatternP2PUnitSettings settings = new PatternP2PUnitSettings(returnMode, breakRecovery,
                ProductExtractionLimits.clampInterval(extractionInterval),
                ProductExtractionLimits.clampAmount(extractionAmount), redstoneMode,
                Math.max(0, Math.min(15, redstoneStrength)),
                Math.max(1, Math.min(period, pulseWidth)), period,
                transferPortOutputMode, outputSlotSharingMode);
        ModNetwork.sendPatternSettings(menu, extractionEnabled, settings, syncInputSettings, resetTask);
    }

    @Override
    protected void renderBg(MatrixStack matrices, float partialTick, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(matrices, leftPos, topPos, imageWidth, imageHeight);
        if (output) {
            section(matrices, "gui.ae2_batchcraft.return_mode", 43, 74);
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 85, 116);
        } else if (page == Page.COMMON) {
            section(matrices, "gui.ae2_batchcraft.return_configuration", 43, 74);
            section(matrices, "gui.ae2_batchcraft.product_extraction.title", 22, 85, 137);
            if (showSingleSlotControl) {
                section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 146, 178);
            }
        } else if (page == Page.TRANSFER) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 43, 74);
        } else if (page == Page.BREAK) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 43, 74);
        } else {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 43, 74);
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 85, 158);
        }
    }

    @Override
    protected void renderLabels(MatrixStack matrices, int mouseX, int mouseY) {
        font.draw(matrices, title.getString(), 8, 6, 0x404040);
        if (output) {
            sectionTitle(matrices, "gui.ae2_batchcraft.return_mode", 39);
            sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.task_reset", 81);
            return;
        }
        font.draw(matrices, tr("gui.ae2_batchcraft.pattern_p2p_unit.page."
                + page.name().toLowerCase()).getString(), 8, 23, 0x404040);
        if (page == Page.COMMON) {
            sectionTitle(matrices, "gui.ae2_batchcraft.return_configuration", 39);
            sectionTitle(matrices, "gui.ae2_batchcraft.product_extraction.title", 81);
            drawValueLabel(matrices, "gui.ae2_batchcraft.product_extraction.interval", 96, "gui.ae2_batchcraft.time.ticks");
            drawValueLabel(matrices, "gui.ae2_batchcraft.product_extraction.amount", 117, "gui.ae2_batchcraft.product_extraction.unit");
            if (showSingleSlotControl) {
                sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 142);
            }
        } else if (page == Page.TRANSFER) {
            sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 39);
        } else if (page == Page.BREAK) {
            sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
        } else {
            sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 39);
            sectionTitle(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 81);
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength", 96, null);
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.pulse_width", 117, "gui.ae2_batchcraft.time.ticks");
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.pulse_period", 138, "gui.ae2_batchcraft.time.ticks");
        }
    }

    private void section(MatrixStack matrices, String key, int top, int bottom) {
        DashedSectionRenderer.draw(matrices, font, tr(key).getString(),
                leftPos, topPos, imageWidth, top, bottom);
    }

    private void section(MatrixStack matrices, String key, int trailingReservedWidth,
            int top, int bottom) {
        DashedSectionRenderer.draw(matrices, font, tr(key).getString(), trailingReservedWidth,
                leftPos, topPos, imageWidth, top, bottom);
    }

    private void sectionTitle(MatrixStack matrices, String key, int y) {
        DashedSectionRenderer.title(matrices, font, tr(key).getString(), y);
    }

    private void drawValueLabel(MatrixStack matrices, String key, int y, String unit) {
        font.draw(matrices, tr(key).getString(), 12, y, 0x404040);
        if (unit != null) font.draw(matrices, tr(unit).getString(), 143, y, 0x707070);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, partialTick);
        if (!output && page == Page.COMMON) {
            intervalField.render(matrices, mouseX, mouseY, partialTick);
            amountField.render(matrices, mouseX, mouseY, partialTick);
        } else if (!output && page == Page.REDSTONE) {
            strengthField.render(matrices, mouseX, mouseY, partialTick);
            widthField.render(matrices, mouseX, mouseY, partialTick);
            periodField.render(matrices, mouseX, mouseY, partialTick);
        }
        renderTooltip(matrices, mouseX, mouseY);
        if (resetTaskButton != null && resetTaskButton.isHovered()) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.reset_task.tooltip"), mouseX, mouseY);
        }
    }

    private static int heightFor(Page page) {
        return page == Page.COMMON ? 186 : page == Page.TRANSFER ? 82 : page == Page.BREAK ? 82 : 166;
    }

    private static int resolveHeight(Page page) {
        int pageCount = Page.values().length;
        int toolbarHeight = PAGE_BUTTON_TOP + pageCount * PAGE_BUTTON_HEIGHT
                + Math.max(0, pageCount - 1) * PAGE_BUTTON_SPACING;
        return Math.max(heightFor(page), toolbarHeight);
    }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static ITextComponent tr(String key) { return new TranslationTextComponent(key); }
}
