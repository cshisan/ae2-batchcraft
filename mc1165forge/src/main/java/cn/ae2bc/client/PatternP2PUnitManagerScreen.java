package cn.ae2bc.client;

import appeng.client.gui.Icon;
import cn.ae2bc.menu.PatternP2PUnitManagerMenu;
import cn.ae2bc.network.ModNetwork;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import cn.ae2bc.core.unit.TransferPortOutputMode;
import cn.ae2bc.logic.EnergyDistributionMode;
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

/** 1.16 implementation of the paged 1.21 unit-manager configuration screen. */
public final class PatternP2PUnitManagerScreen extends ContainerScreen<PatternP2PUnitManagerMenu> {
    private static final int PAGE_BUTTON_TOP = 20;
    private static final int PAGE_BUTTON_HEIGHT = 20;
    private static final int PAGE_BUTTON_SPACING = 2;
    private enum Page { COMMON, UNIT_COMMON, TRANSFER, BREAK, REDSTONE, ENERGY }

    private Page page = Page.COMMON;
    private ReturnMode returnMode;
    private boolean syncMain;
    private OutputSlotSharingMode slotSharingMode;
    private EnergyDistributionMode energyMode;
    private TransferPortOutputMode transferPortOutputMode;
    private boolean breakRecovery;
    private RedstoneOutputMode redstoneMode;
    private int extractionInterval;
    private int extractionAmount;
    private int redstoneStrength;
    private int pulseWidth;
    private int pulsePeriod;
    private Button resetTaskButton;
    private Button commonPageButton;
    private Button unitCommonPageButton;
    private Button pagePortButton;
    private TextFieldWidget intervalField;
    private TextFieldWidget amountField;
    private TextFieldWidget strengthField;
    private TextFieldWidget widthField;
    private TextFieldWidget periodField;

    public PatternP2PUnitManagerScreen(PatternP2PUnitManagerMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 172;
        PatternP2PUnitSettings settings = menu.getSettings();
        syncMain = menu.isSyncMainConfiguration();
        slotSharingMode = menu.getOutputSlotSharingMode();
        energyMode = menu.getEnergyDistributionMode();
        transferPortOutputMode = settings.getTransferPortOutputMode();
        returnMode = settings.getReturnMode();
        breakRecovery = settings.isBreakRecovery();
        extractionInterval = settings.getExtractionInterval();
        extractionAmount = settings.getExtractionAmount();
        redstoneMode = settings.getRedstoneMode();
        redstoneStrength = settings.getRedstoneStrength();
        pulseWidth = settings.getPulseWidthTicks();
        pulsePeriod = settings.getPulsePeriodTicks();
        imageHeight = resolveHeight(page);
    }

    @Override
    protected void init() {
        imageHeight = resolveHeight(page);
        super.init();
        addButton(new Ae2Button(leftPos + 148, topPos - 5, 20, 20,
                new StringTextComponent("X"), button -> onClose()));
        commonPageButton = addPageIconButton(2, Page.COMMON, 20, Icon.PERMISSION_BUILD.ordinal());
        unitCommonPageButton = addPageIconButton(3, Page.UNIT_COMMON, 42,
                Icon.PERMISSION_BUILD_DISABLED.ordinal());
        pagePortButton = addButton(new Ae2IconButton(leftPos - 24, topPos + 64,
                Icon.ARROW_RIGHT.ordinal(), 1.0F, -1, -1, pressed -> {
                    page = nextPortPage();
                    init(minecraft, width, height);
                }));
        pagePortButton.setMessage(new TranslationTextComponent(
                "gui.ae2_batchcraft.pattern_p2p_unit.page.switch", pageName(nextPortPage())));
        resetTaskButton = addButton(new Ae2IconButton(leftPos + imageWidth + 2, topPos + 17,
                Icon.INVALID.ordinal(), ignored -> TaskResetConfirmation.open(this,
                        tr("gui.ae2_batchcraft.reset_task.confirm.unit"),
                        () -> sendCurrentSettings(true))));
        resetTaskButton.setMessage(tr("gui.ae2_batchcraft.reset_task.tooltip.unit"));
        if (page == Page.COMMON) initCommon();
        else if (page == Page.UNIT_COMMON) initUnitCommon();
        else if (page == Page.TRANSFER) initTransfer();
        else if (page == Page.BREAK) initBreak();
        else if (page == Page.REDSTONE) initRedstone();
        else initEnergy();
    }

    private void initTransfer() {
        addButton(transferButton(12, TransferPortOutputMode.NORMAL));
        addButton(transferButton(64, TransferPortOutputMode.SINGLE_ITEM));
        addButton(transferButton(116, TransferPortOutputMode.SAME_TYPE));
    }

    private Button transferButton(int x, TransferPortOutputMode mode) {
        Button button = new Ae2Button(leftPos + x, topPos + 50, 48, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.transfer_mode." + mode.getSerializedName()), pressed -> {
                    transferPortOutputMode = mode;
                    sendCurrentSettings(false);
                    init(minecraft, width, height);
                });
        button.active = !syncMain && transferPortOutputMode != mode;
        return button;
    }

    private void initCommon() {
        ITextComponent syncLabel = tr("gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration");
        int syncWidth = font.width(syncLabel) + ToggleSwitch.LABEL_OFFSET;
        addButton(new ToggleSwitch(leftPos + imageWidth - 8 - syncWidth, topPos + 21,
                syncWidth, syncLabel, syncMain, selected -> {
                syncMain = selected;
                    sendCurrentSettings(false);
                    init(minecraft, width, height);
                }));
        addButton(modeButton(12, 50, 73, ReturnMode.STRICT));
        addButton(modeButton(91, 50, 73, ReturnMode.UNBLOCKED));
        intervalField = field(104, 92, extractionInterval,
                ProductExtractionLimits.MIN_INTERVAL, () -> ProductExtractionLimits.MAX_INTERVAL,
                value -> extractionInterval = value);
        amountField = field(104, 113, extractionAmount,
                ProductExtractionLimits.MIN_AMOUNT, () -> ProductExtractionLimits.MAX_AMOUNT,
                value -> extractionAmount = value);
        setCommonEditable(!syncMain);
    }

    private void initUnitCommon() {
        Button all = addButton(new ScrollingAe2Button(leftPos + 12, topPos + 50, 46, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.all"),
                button -> { slotSharingMode = OutputSlotSharingMode.ALL; sendCurrentSettings(false); init(minecraft, width, height); }));
        Button disabled = addButton(new ScrollingAe2Button(leftPos + 63, topPos + 50, 46, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.disabled"),
                button -> { slotSharingMode = OutputSlotSharingMode.DISABLED; sendCurrentSettings(false); init(minecraft, width, height); }));
        Button follow = addButton(new ScrollingAe2Button(leftPos + 114, topPos + 50, 46, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.single_slot.follow_port"),
                button -> { slotSharingMode = OutputSlotSharingMode.FOLLOW_PORT; sendCurrentSettings(false); init(minecraft, width, height); }));
        all.active = !syncMain && slotSharingMode != OutputSlotSharingMode.ALL;
        disabled.active = !syncMain && slotSharingMode != OutputSlotSharingMode.DISABLED;
        follow.active = !syncMain && slotSharingMode != OutputSlotSharingMode.FOLLOW_PORT;
    }

    private void initBreak() {
        ITextComponent label = tr("gui.ae2_batchcraft.pattern_p2p_unit.break_recovery");
        ToggleSwitch checkbox = addButton(new ToggleSwitch(leftPos + 12, topPos + 52,
                font.width(label) + ToggleSwitch.LABEL_OFFSET,
                label, breakRecovery, selected -> {
                    breakRecovery = selected;
                    sendCurrentSettings(false);
                }));
        checkbox.active = !syncMain;
    }

    private void initRedstone() {
        addButton(redstoneButton(12, RedstoneOutputMode.SINGLE_TRIGGER));
        addButton(redstoneButton(64, RedstoneOutputMode.PERIODIC_PULSE));
        addButton(redstoneButton(116, RedstoneOutputMode.CONTINUOUS));
        strengthField = field(104, 92, redstoneStrength, 0, () -> 15,
                value -> redstoneStrength = value);
        widthField = field(104, 113, pulseWidth, 1, () -> pulsePeriod,
                value -> pulseWidth = value);
        periodField = field(104, 134, pulsePeriod, 1, () -> PatternP2PUnitSettings.MAX_PULSE_TICKS,
                value -> pulsePeriod = value);
        strengthField.setEditable(!syncMain);
        widthField.setEditable(!syncMain);
        periodField.setEditable(!syncMain);
    }

    private void initEnergy() {
        Button modeButton = addButton(new Ae2Button(leftPos + 12, topPos + 50, 152, 20,
                tr("gui.ae2_batchcraft.energy_distribution_mode." + energyMode.getSerializedName()),
                pressed -> { energyMode = energyMode.next(); sendCurrentSettings(false); init(minecraft, width, height); }));
        modeButton.active = !syncMain;
    }

    private Button modeButton(int x, int y, int width, ReturnMode mode) {
        Button button = new Ae2Button(leftPos + x, topPos + y, width, 20,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + mode.getSerializedName()), pressed -> {
                    returnMode = mode;
                    sendCurrentSettings(false);
                    init(minecraft, this.width, this.height);
                });
        button.active = !syncMain && returnMode != mode;
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
        button.active = !syncMain && redstoneMode != mode;
        return button;
    }

    private void setCommonEditable(boolean editable) {
        intervalField.setEditable(editable);
        amountField.setEditable(editable);
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

    private Button addPageIconButton(int id, Page target, int y, int iconIndex) {
        return addPageIconButton(id, target, y, iconIndex, 1.0F);
    }

    private Button addPageIconButton(int id, Page target, int y, int iconIndex,
                                     int iconOffsetX, int iconOffsetY) {
        return addPageIconButton(id, target, y, iconIndex, 1.0F, iconOffsetX, iconOffsetY);
    }

    private Button addPageIconButton(int id, Page target, int y, int iconIndex, float iconScale) {
        return addPageIconButton(id, target, y, iconIndex, iconScale, 0, 0);
    }

    private Button addPageIconButton(int id, Page target, int y, int iconIndex, float iconScale,
                                     int iconOffsetX, int iconOffsetY) {
        Button button = new Ae2IconButton(leftPos - 24, topPos + y, iconIndex, iconScale,
                iconOffsetX, iconOffsetY, pressed -> {
            captureFields();
            page = target;
            init(minecraft, width, height);
        });
        button.active = page != target;
        button.setMessage(pageName(target));
        addButton(button);
        return button;
    }

    private void switchPage() {
        captureFields();
        page = page == Page.COMMON ? Page.UNIT_COMMON : page == Page.UNIT_COMMON ? Page.TRANSFER : page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE : page == Page.REDSTONE ? Page.ENERGY : Page.COMMON;
        init(minecraft, width, height);
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
        ModNetwork.sendUnitManagerSettings(menu, menu.getFrequency(), settings, syncMain, energyMode, resetTask);
    }

    @Override
    protected void renderBg(MatrixStack matrices, float partialTick, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(matrices, leftPos, topPos, imageWidth, imageHeight);
        if (page == Page.COMMON) {
            section(matrices, "gui.ae2_batchcraft.return_configuration", 43, 74);
            section(matrices, "gui.ae2_batchcraft.product_extraction.parameters", 85, 137);
        } else if (page == Page.UNIT_COMMON) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 43, 74);
        } else if (page == Page.TRANSFER) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 43, 74);
        } else if (page == Page.BREAK) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 43, 74);
        } else if (page == Page.REDSTONE) {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 43, 74);
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 85, 158);
        } else {
            section(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 43, 74);
        }
    }

    @Override
    protected void renderLabels(MatrixStack matrices, int mouseX, int mouseY) {
        font.draw(matrices, title.getString(), 8, 6, 0x404040);
        font.draw(matrices, tr("gui.ae2_batchcraft.pattern_p2p_unit.page."
                + page.name().toLowerCase()).getString(), 8, 23, 0x404040);
        if (page == Page.COMMON) {
            labels(matrices, "gui.ae2_batchcraft.return_configuration", 39);
            labels(matrices, "gui.ae2_batchcraft.product_extraction.parameters", 81);
            drawValueLabel(matrices, "gui.ae2_batchcraft.product_extraction.interval", 96, "gui.ae2_batchcraft.time.ticks");
            drawValueLabel(matrices, "gui.ae2_batchcraft.product_extraction.amount", 117, "gui.ae2_batchcraft.product_extraction.unit");
        } else if (page == Page.UNIT_COMMON) {
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.single_slot", 39);
        } else if (page == Page.TRANSFER) {
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.transfer_mode", 39);
        } else if (page == Page.BREAK) {
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.drop_handling", 39);
        } else if (page == Page.REDSTONE) {
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode", 39);
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.signal_parameters", 81);
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_strength", 96, null);
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.pulse_width", 117, "gui.ae2_batchcraft.time.ticks");
            drawValueLabel(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.pulse_period", 138, "gui.ae2_batchcraft.time.ticks");
        } else {
            labels(matrices, "gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration", 39);
        }
    }

    private void section(MatrixStack matrices, String key, int top, int bottom) {
        DashedSectionRenderer.draw(matrices, font, tr(key).getString(),
                leftPos, topPos, imageWidth, top, bottom);
    }

    private void labels(MatrixStack matrices, String key, int y) {
        DashedSectionRenderer.title(matrices, font, tr(key).getString(), y);
    }

    private void drawValueLabel(MatrixStack matrices, String key, int y, String unitKey) {
        font.draw(matrices, tr(key).getString(), 12, y, 0x404040);
        if (unitKey != null) font.draw(matrices, tr(unitKey).getString(), 143, y, 0x707070);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, partialTick);
        if (page == Page.COMMON) {
            intervalField.render(matrices, mouseX, mouseY, partialTick);
            amountField.render(matrices, mouseX, mouseY, partialTick);
        } else if (page == Page.REDSTONE) {
            strengthField.render(matrices, mouseX, mouseY, partialTick);
            widthField.render(matrices, mouseX, mouseY, partialTick);
            periodField.render(matrices, mouseX, mouseY, partialTick);
        }
        renderTooltip(matrices, mouseX, mouseY);
        if (resetTaskButton != null && resetTaskButton.isHovered()) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.reset_task.tooltip.unit"), mouseX, mouseY);
        }
        renderIconTooltip(matrices, commonPageButton, mouseX, mouseY);
        renderIconTooltip(matrices, unitCommonPageButton, mouseX, mouseY);
        renderIconTooltip(matrices, pagePortButton, mouseX, mouseY);
    }

    private void renderIconTooltip(MatrixStack matrices, Button button, int mouseX, int mouseY) {
        if (button != null && button.visible && button.isHovered()) {
            renderTooltip(matrices, button.getMessage(), mouseX, mouseY);
        }
    }

    private static ITextComponent pageName(Page page) {
        return tr("gui.ae2_batchcraft.pattern_p2p_unit.page."
                + page.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int heightFor(Page page) {
        return page == Page.COMMON ? 146 : page == Page.REDSTONE ? 166 : 82;
    }

    private static int resolveHeight(Page page) {
        int pageCount = 3;
        int toolbarHeight = PAGE_BUTTON_TOP + pageCount * PAGE_BUTTON_HEIGHT
                + Math.max(0, pageCount - 1) * PAGE_BUTTON_SPACING;
        return Math.max(heightFor(page), toolbarHeight);
    }

    private static int parse(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private Page nextPortPage() {
        return page == Page.TRANSFER ? Page.BREAK
                : page == Page.BREAK ? Page.REDSTONE
                : page == Page.REDSTONE ? Page.ENERGY : Page.TRANSFER;
    }

    private static ITextComponent tr(String key) { return new TranslationTextComponent(key); }

}
