package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import java.util.EnumMap;
import java.util.Map;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

abstract class PatternP2PUnitPagedScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    private static final int TOOLBAR_MARGIN = 2;
    private static final int TOOLBAR_SPACING = 4;
    private static final int HELP_BUTTON_HEIGHT = 20;

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

    protected enum PageGroup { OUTPUT, UNIT }

    private final Map<Page, Button> pageButtons = new EnumMap<>(Page.class);
    private Page page = Page.COMMON;
    private final boolean showPageGroupButtons;
    private final ScaledTabButton pageGroupButton;
    private final IconButton unitPortPageButton;
    private PageGroup pageGroup;

    protected PatternP2PUnitPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style,
                                        boolean showPageGroupButtons, PageGroup initialPageGroup) {
        super(menu, inventory, title, style);
        this.showPageGroupButtons = showPageGroupButtons;
        this.pageGroup = initialPageGroup;
        var closeButton = new TabButton(Icon.CLEAR,
                Component.translatable("gui.ae2_batchcraft.configuration.close"), button -> onClose());
        widgets.add("close", closeButton);

        if (showPageGroupButtons) {
            pageGroupButton = new ScaledTabButton(Icon.ARROW_RIGHT, 1.0f,
                    Component.empty(), ignored -> selectPageGroup(
                            pageGroup == PageGroup.OUTPUT ? PageGroup.UNIT : PageGroup.OUTPUT));
            addToRightToolbar("pageGroup", pageGroupButton);
        } else {
            pageGroupButton = null;
        }
        for (Page candidate : Page.values()) {
            if (isUnitPortPage(candidate)) continue;
            if (candidate == Page.ENERGY && !supportsEnergyPage()) {
                continue;
            }
            Button button = new IconButton(ignored -> selectPage(candidate)) {
                @Override protected Icon getIcon() { return pageIcon(candidate); }
            };
            button.setMessage(Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." + candidate.serializedName));
            pageButtons.put(candidate, button);
            addToLeftToolbar(button);
        }
        if (supportsUnitPages()) {
            unitPortPageButton = new IconButton(ignored -> selectPage(nextUnitPortPage())) {
                @Override protected Icon getIcon() { return Icon.ARROW_RIGHT; }
            };
            addToLeftToolbar(unitPortPageButton);
        } else {
            unitPortPageButton = null;
        }
    }

    @Override
    protected void init() {
        updatePageVisibility();
        imageHeight = resolvePageHeight(page);
        super.init();
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
        pageButtons.forEach((candidate, button) -> button.visible = isPageInCurrentGroup(candidate));
        if (pageGroupButton != null) {
            pageGroupButton.visible = showPageGroupButtons;
            pageGroupButton.setMessage(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.page_group.switch",
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page_group." +
                            (pageGroup == PageGroup.OUTPUT ? "unit" : "output"))));
        }
        if (unitPortPageButton != null) {
            unitPortPageButton.visible = pageGroup == PageGroup.UNIT;
            unitPortPageButton.setMessage(Component.translatable(
                    "gui.ae2_batchcraft.pattern_p2p_unit.page.switch",
                    Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." +
                            nextUnitPortPage().serializedName)));
        }
        setTextContent("page_title", Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.page." + page.serializedName));
    }

    protected final boolean isPage(Page candidate) {
        return page == candidate;
    }

    protected final boolean isPageGroup(PageGroup candidate) { return pageGroup == candidate; }

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
        if (pageGroup == next) return;
        pageGroup = next;
        page = Page.COMMON;
        updatePageVisibility();
        rebuildPageLayout();
    }

    private int resolvePageHeight(Page candidate) {
        return Math.max(getPageHeight(candidate), getToolbarHeight());
    }

    private int getToolbarHeight() {
        int toolbarHeight = 0;
        if (getHelpTopic() != null) {
            toolbarHeight += HELP_BUTTON_HEIGHT + TOOLBAR_SPACING;
        }
        for (Button button : pageButtons.values()) {
            if (button.visible) {
                toolbarHeight += button.getHeight() + TOOLBAR_SPACING;
            }
        }
        if (unitPortPageButton != null && unitPortPageButton.visible) {
            toolbarHeight += unitPortPageButton.getHeight() + TOOLBAR_SPACING;
        }
        if (toolbarHeight == 0) {
            return 0;
        }
        Integer configuredTop = style.getWidget("verticalToolbar").getTop();
        int toolbarTop = configuredTop == null ? 0 : configuredTop;
        // Keep the page bottom flush with AE2's VerticalButtonBar bounds. The bar starts two pixels below its
        // configured top and includes the trailing four-pixel spacing after the last visible button.
        return toolbarTop + TOOLBAR_MARGIN + toolbarHeight;
    }

    private void rebuildPageLayout() {
        updatePageVisibility();
        imageHeight = resolvePageHeight(page);
        repositionElements();
    }

    private static Icon pageIcon(Page page) {
        return switch (page) {
            case COMMON -> Icon.WRENCH;
            case OUTPUT_COMMON, UNIT_COMMON -> Icon.FULLNESS_FULL;
            case TRANSFER, BREAK, REDSTONE, ENERGY -> Icon.ARROW_RIGHT;
        };
    }

    private boolean isPageInCurrentGroup(Page candidate) {
        if (candidate == Page.COMMON) return true;
        return pageGroup == PageGroup.OUTPUT ? candidate == Page.OUTPUT_COMMON : candidate != Page.OUTPUT_COMMON;
    }

    private static boolean isUnitPortPage(Page page) {
        return page == Page.TRANSFER || page == Page.BREAK || page == Page.REDSTONE || page == Page.ENERGY;
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

    protected final <B extends TabButton> B addToRightToolbar(String widgetId, B button) {
        button.setStyle(TabButton.Style.HORIZONTAL);
        widgets.add(widgetId, button);
        return button;
    }

}
