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
        OUTPUT_COMMON("output_common"),
        UNIT_COMMON("unit_common"),
        TRANSFER("transfer"),
        BREAK("break"),
        REDSTONE("redstone"),
        ENERGY("energy");

        private final String serializedName;

        Page(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    protected enum PageGroup {
        OUTPUT,
        UNIT
    }

    private final Map<Page, IconButton> pageButtons = new EnumMap<>(Page.class);
    private final RightToolbarPanel rightToolbar = new RightToolbarPanel();
    private final boolean showPageGroupButtons;
    private final ScaledIconButton pageGroupButton;
    private final ScaledIconButton unitPortPageButton;
    private PageGroup pageGroup;
    private Page page = Page.COMMON;
    private boolean relayoutInProgress;
    private int relayoutHeight;

    protected PatternP2PUnitPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style,
                                        boolean showPageGroupButtons, PageGroup initialPageGroup) {
        super(menu, inventory, title, style);
        this.showPageGroupButtons = showPageGroupButtons;
        this.pageGroup = initialPageGroup;
        var closeButton = new TabButton(Icon.CLEAR,
                Component.translatable("gui.ae2_batchcraft.configuration.close"), button -> onClose());
        widgets.add("close", closeButton);
        widgets.add("rightToolbar", rightToolbar);

        if (showPageGroupButtons) {
            pageGroupButton = new RightToolbarIconButton(Icon.ARROW_RIGHT, 1.0f,
                    ignored -> selectPageGroup(pageGroup == PageGroup.OUTPUT
                            ? PageGroup.UNIT : PageGroup.OUTPUT));
            pageGroupButton.setDimWhenInactive(false);
            addToRightToolbar("pageGroup", pageGroupButton);
        } else {
            pageGroupButton = null;
        }

        for (Page candidate : Page.values()) {
            if (isUnitPortPage(candidate)) {
                continue;
            }
            ScaledIconButton button = createPageButton(candidate);
            button.setDimWhenInactive(false);
            button.setMessage(Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." + candidate.serializedName));
            pageButtons.put(candidate, button);
            addToLeftToolbar(button);
        }

        if (supportsUnitPages()) {
            unitPortPageButton = new ScaledIconButton(Icon.ARROW_RIGHT, 1.0f,
                    ignored -> selectPage(nextUnitPortPage()));
            unitPortPageButton.setDimWhenInactive(false);
            addToLeftToolbar(unitPortPageButton);
        } else {
            unitPortPageButton = null;
        }
    }

    @Override
    protected void init() {
        updateNavigationVisibility();
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
        updateNavigationVisibility();
        pageButtons.forEach((candidate, button) -> button.active = candidate != page);
        if (pageGroupButton != null) {
            PageGroup nextGroup = pageGroup == PageGroup.OUTPUT ? PageGroup.UNIT : PageGroup.OUTPUT;
            pageGroupButton.active = true;
            pageGroupButton.setMessage(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.page_group.switch",
                    pageGroupName(nextGroup)));
        }
        if (unitPortPageButton != null) {
            unitPortPageButton.active = true;
            unitPortPageButton.setMessage(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.page.switch",
                    pageName(nextUnitPortPage())));
        }
        setTextContent("page_title", Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.page." + page.serializedName));
    }

    protected final boolean isPage(Page candidate) {
        return page == candidate;
    }

    protected final boolean isPageGroup(PageGroup candidate) {
        return pageGroup == candidate;
    }

    protected abstract int getPageHeight(Page candidate);

    protected boolean supportsEnergyPage() {
        return true;
    }

    protected boolean supportsUnitPages() {
        return true;
    }

    protected abstract void updatePageVisibility();

    private void selectPage(Page next) {
        if (page == next || !isPageInCurrentGroup(next)) {
            return;
        }
        page = next;
        updatePageVisibility();
        rebuildPageLayout();
    }

    private void selectPageGroup(PageGroup next) {
        if (pageGroup == next) {
            return;
        }
        pageGroup = next;
        if (page != Page.COMMON) {
            page = Page.COMMON;
        }
        updateNavigationVisibility();
        updatePageVisibility();
        rebuildPageLayout();
    }

    private int resolvePageHeight(Page candidate) {
        return Math.max(Math.max(getPageHeight(candidate), getToolbarHeightFromAe2()),
                rightToolbar.getRequiredHeight());
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
        if (unitPortPageButton != null && unitPortPageButton.visible) {
            toolbarHeight += unitPortPageButton.getHeight() + 6;
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
        updateNavigationVisibility();
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

    private ScaledIconButton createPageButton(Page page) {
        float scale = switch (page) {
            case Page.OUTPUT_COMMON, Page.UNIT_COMMON -> 0.6f;
            default -> 1.0f;
        };
        return new ScaledIconButton(pageIcon(page), scale, ignored -> selectPage(page));
    }

    private void updateNavigationVisibility() {
        pageButtons.forEach((candidate, button) -> button.visible = isPageInCurrentGroup(candidate));
        if (pageGroupButton != null) {
            pageGroupButton.visible = showPageGroupButtons;
        }
        if (unitPortPageButton != null) {
            unitPortPageButton.visible = pageGroup == PageGroup.UNIT;
        }
    }

    private boolean isPageInCurrentGroup(Page candidate) {
        if (candidate == Page.COMMON) {
            return true;
        }
        return switch (pageGroup) {
            case OUTPUT -> candidate == Page.OUTPUT_COMMON;
            case UNIT -> candidate != Page.OUTPUT_COMMON;
        };
    }

    private static Icon pageIcon(Page page) {
        return switch (page) {
            case COMMON -> Icon.COG;
            case OUTPUT_COMMON, UNIT_COMMON -> Icon.S_MACHINE;
            case TRANSFER, BREAK, REDSTONE, ENERGY -> Icon.ARROW_RIGHT;
        };
    }

    private Page nextUnitPortPage() {
        return switch (page) {
            case TRANSFER -> Page.BREAK;
            case BREAK -> Page.REDSTONE;
            case REDSTONE -> supportsEnergyPage() ? Page.ENERGY : Page.TRANSFER;
            case ENERGY -> Page.TRANSFER;
            default -> Page.TRANSFER;
        };
    }

    private static boolean isUnitPortPage(Page candidate) {
        return candidate == Page.TRANSFER || candidate == Page.BREAK
                || candidate == Page.REDSTONE || candidate == Page.ENERGY;
    }

    private static Component pageName(Page page) {
        return Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.page." + page.serializedName);
    }

    private static Component pageGroupName(PageGroup group) {
        return Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page_group."
                + group.name().toLowerCase(java.util.Locale.ROOT));
    }

    protected final <B extends IconButton> B addToRightToolbar(String widgetId, B button) {
        rightToolbar.addButton(button);
        return button;
    }

}
