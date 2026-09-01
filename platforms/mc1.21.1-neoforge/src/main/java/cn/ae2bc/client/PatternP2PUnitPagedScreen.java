package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.VerticalButtonBar;
import cn.ae2bc.mixin.AEBaseScreenAccessor;
import cn.ae2bc.mixin.WidgetContainerAccessor;
import java.util.EnumMap;
import java.util.Map;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Inventory;

abstract class PatternP2PUnitPagedScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    private static final int TOOLBAR_BACKGROUND_BOTTOM_PADDING = 3;

    protected enum Page {
        COMMON("common"),
        TRANSFER("transfer"),
        BREAK("break"),
        REDSTONE("redstone"),
        ENERGY("energy");

        private final String serializedName;

        Page(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    private final Map<Page, IconButton> pageButtons = new EnumMap<>(Page.class);
    private final RightToolbarPanel rightToolbar = new RightToolbarPanel();
    private Page page = Page.COMMON;
    private boolean relayoutInProgress;
    private int relayoutHeight;

    protected PatternP2PUnitPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        var closeButton = new TabButton(Icon.CLEAR,
                Component.translatable("gui.ae2_batchcraft.configuration.close"), button -> onClose());
        widgets.add("close", closeButton);
        widgets.add("rightToolbar", rightToolbar);

        for (Page candidate : Page.values()) {
            if (candidate == Page.ENERGY && !supportsEnergyPage()) {
                continue;
            }
            ScaledIconButton button = new ScaledIconButton(pageIcon(candidate), pageIconScale(candidate),
                    pageIconOffsetX(candidate), pageIconOffsetY(candidate),
                    ignored -> selectPage(candidate));
            button.setDimWhenInactive(false);
            button.setMessage(Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." + candidate.serializedName));
            pageButtons.put(candidate, button);
            addToLeftToolbar(button);
        }
    }

    @Override
    protected void init() {
        updatePageVisibility();
        imageHeight = relayoutInProgress ? relayoutHeight : getPageHeight(page);
        super.init();
    }

    /** Completes the first layout after the subclass has created its delayed controls. */
    protected final void completeInitialLayout() {
        if (relayoutInProgress) {
            return;
        }

        int resolvedHeight = resolvePageHeight(page);
        if (resolvedHeight == imageHeight) {
            return;
        }

        relayoutHeight = resolvedHeight;
        relayoutInProgress = true;
        try {
            imageHeight = resolvedHeight;
            repositionElements();
        } finally {
            relayoutInProgress = false;
        }
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        BackgroundGenerator.draw(imageWidth, imageHeight, guiGraphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        guiGraphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        pageButtons.forEach((candidate, button) -> button.active = candidate != page);
        setTextContent("page_title", Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.page." + page.serializedName));
    }

    protected final boolean isPage(Page candidate) {
        return page == candidate;
    }

    protected abstract int getPageHeight(Page candidate);

    protected boolean supportsEnergyPage() {
        return true;
    }

    protected abstract void updatePageVisibility();

    private void selectPage(Page next) {
        if (page == next) {
            return;
        }
        page = next;
        updatePageVisibility();
        rebuildPageLayout();
    }

    private int resolvePageHeight(Page candidate) {
        return Math.max(getPageHeight(candidate), getToolbarHeightFromAe2());
    }

    private int getToolbarHeightFromAe2() {
        VerticalButtonBar toolbar = getVerticalToolbar();
        if (toolbar != null) {
            toolbar.updateBeforeRender();
            Rect2i bounds = toolbar.getBounds();
            if (bounds.getHeight() > 0) {
                // VerticalButtonBar draws from bounds.y - 1 with bounds.height + 4 pixels.
                return bounds.getY() + bounds.getHeight() + TOOLBAR_BACKGROUND_BOTTOM_PADDING;
            }
        }

        return getToolbarHeightFallback();
    }

    private int getToolbarHeightFallback() {
        int toolbarHeight = 0;
        for (Button button : pageButtons.values()) {
            if (button.visible) {
                toolbarHeight += button.getHeight() + 6;
            }
        }
        if (toolbarHeight == 0) {
            return 0;
        }
        var toolbarStyle = style.getWidget("verticalToolbar");
        Integer configuredTop = toolbarStyle == null ? null : toolbarStyle.getTop();
        int toolbarTop = configuredTop == null ? 0 : configuredTop;
        return toolbarTop + 2 + toolbarHeight + TOOLBAR_BACKGROUND_BOTTOM_PADDING;
    }

    private VerticalButtonBar getVerticalToolbar() {
        WidgetContainerAccessor widgets = (WidgetContainerAccessor) (Object)
                ((AEBaseScreenAccessor) (Object) this).ae2bc$getWidgets();
        var composite = widgets.ae2bc$getCompositeWidgetsById().get("verticalToolbar");
        return composite instanceof VerticalButtonBar toolbar ? toolbar : null;
    }

    private void rebuildPageLayout() {
        updatePageVisibility();
        if (relayoutInProgress) {
            return;
        }

        int resolvedHeight = resolvePageHeight(page);
        if (resolvedHeight == imageHeight) {
            return;
        }

        relayoutHeight = resolvedHeight;
        relayoutInProgress = true;
        try {
            imageHeight = resolvedHeight;
            repositionElements();
        } finally {
            relayoutInProgress = false;
        }
    }

    private static Icon pageIcon(Page page) {
        return switch (page) {
            case COMMON -> Icon.COG;
            case TRANSFER -> Icon.ACCESS_WRITE;
            case BREAK -> Icon.PLACEMENT_BLOCK;
            case REDSTONE -> Icon.REDSTONE_ON;
            case ENERGY -> Icon.POWER_UNIT_RF;
        };
    }

    private static float pageIconScale(Page page) {
        return switch (page) {
            case TRANSFER -> 1.1f;
            case BREAK -> 0.9f;
            default -> 1.0f;
        };
    }

    private static int pageIconOffsetX(Page page) {
        return 0;
    }

    private static int pageIconOffsetY(Page page) {
        return page == Page.TRANSFER ? 1 : 0;
    }

    protected final <B extends IconButton> B addToRightToolbar(String widgetId, B button) {
        rightToolbar.addButton(button);
        return button;
    }

}
