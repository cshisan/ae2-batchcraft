package cn.ae2bc.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import cn.ae2bc.logic.ReturnMode;
import cn.ae2bc.menu.PatternP2PTunnelOutputMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public final class PatternP2PTunnelOutputScreen extends PatternP2PUnitPagedScreen<PatternP2PTunnelOutputMenu> {
    private final VerticallyAlignedCheckbox syncInputSettings;
    private final AE2Button productExtractionEnabled;
    private final AE2Button productExtractionDisabled;
    private final IconButton resetTaskToolbar;
    private final Map<ReturnMode, AE2Button> returnButtons = new EnumMap<>(ReturnMode.class);
    private ProductExtractionControls extractionControls;

    public PatternP2PTunnelOutputScreen(PatternP2PTunnelOutputMenu menu, Inventory playerInventory,
                                          Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style, false, PageGroup.OUTPUT);
        for (ReturnMode mode : ReturnMode.values()) {
            String modeName = mode.getSerializedName();
            var button = widgets.addButton("return" + Character.toUpperCase(modeName.charAt(0))
                            + modeName.substring(1),
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.return_mode." + modeName),
                    () -> menu.setReturnMode(mode));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.return_mode." + modeName + ".tooltip")));
            returnButtons.put(mode, button);
        }
        syncInputSettings = new VerticallyAlignedCheckbox(style,
                Component.translatable("gui.ae2_batchcraft.sync_input_settings"));
        widgets.add("syncInputSettings", syncInputSettings);
        syncInputSettings.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.sync_input_settings.tooltip")));
        syncInputSettings.setChangeListener(() -> menu.setSyncInputSettings(syncInputSettings.isSelected()));
        productExtractionEnabled = widgets.addButton("productExtractionEnabled",
                Component.translatable("gui.ae2_batchcraft.enabled"),
                () -> menu.setProductExtractionEnabled(true));
        productExtractionEnabled.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.output.enabled.tooltip")));
        productExtractionDisabled = widgets.addButton("productExtractionDisabled",
                Component.translatable("gui.ae2_batchcraft.disabled"),
                () -> menu.setProductExtractionEnabled(false));
        productExtractionDisabled.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.product_extraction.output.enabled.tooltip")));
        resetTaskToolbar = new RightToolbarIconButton(appeng.client.gui.Icon.SCHEDULING_DEFAULT,
                0.9f, ignored -> TaskResetConfirmation.open(this,
                Component.translatable("gui.ae2_batchcraft.reset_task.confirm.output"),
                menu::resetTaskState));
        resetTaskToolbar.setMessage(Component.translatable("gui.ae2_batchcraft.reset_task.tooltip"));
        addToRightToolbar("resetTaskToolbar", resetTaskToolbar);
    }

    @Override
    protected void init() {
        super.init();
        int syncWidth = syncInputSettings.fitToMessage();
        syncInputSettings.setX(leftPos + DashedSectionRenderer.trailingContentX(imageWidth, syncWidth));
        syncInputSettings.setY(topPos + 21);
        extractionControls = ProductExtractionControls.create(font, leftPos, topPos,
                this::addRenderableWidget, menu::setProductExtractionInterval,
                menu::setProductExtractionAmount);
        updatePageVisibility();
        completeInitialLayout();
    }

    @Override
    protected int getPageHeight(Page page) {
        return page == Page.COMMON ? 146 : 82;
    }

    @Override
    protected boolean supportsUnitPages() {
        return false;
    }

    @Override
    protected void updatePageVisibility() {
        boolean common = isPage(Page.COMMON);
        boolean outputCommon = isPage(Page.OUTPUT_COMMON);
        syncInputSettings.visible = common;
        productExtractionEnabled.visible = outputCommon;
        productExtractionDisabled.visible = outputCommon;
        for (AE2Button button : returnButtons.values()) {
            button.visible = common;
        }
        if (extractionControls != null) {
            extractionControls.setVisible(common);
        }
        setTextHidden("extraction_interval", !common);
        setTextHidden("extraction_amount", !common);
        resetTaskToolbar.visible = true;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        syncInputSettings.setSelected(menu.syncInputSettings);
        boolean editable = !menu.syncInputSettings;
        for (var entry : returnButtons.entrySet()) {
            entry.getValue().active = editable && entry.getKey() != menu.returnMode;
        }
        productExtractionEnabled.active = editable && !menu.productExtractionEnabled;
        productExtractionDisabled.active = editable && menu.productExtractionEnabled;
        if (extractionControls != null) {
            extractionControls.setEditable(editable);
            extractionControls.sync(menu.productExtractionInterval, menu.productExtractionAmount);
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        if (isPage(Page.COMMON)) {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.return_mode"),
                    offsetX, offsetY, imageWidth, 43, 74);
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"),
                    offsetX, offsetY, imageWidth, 85, 137);
        } else {
            DashedSectionRenderer.drawBackground(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"),
                    offsetX, offsetY, imageWidth, 43, 74);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (isPage(Page.COMMON)) {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.return_mode"), 39);
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"), 81);
            extractionControls.drawUnits(graphics, font, leftPos);
        } else {
            DashedSectionRenderer.drawTitle(graphics, font,
                    Component.translatable("gui.ae2_batchcraft.product_extraction.title"), 39);
        }
    }
}
