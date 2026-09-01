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
    private Page page = Page.COMMON;

    protected PatternP2PUnitPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        var closeButton = new TabButton(Icon.CLEAR,
                Component.translatable("gui.ae2_batchcraft.configuration.close"), button -> onClose());
        widgets.add("close", closeButton);

        for (Page candidate : Page.values()) {
            if (candidate == Page.ENERGY && !supportsEnergyPage()) {
                continue;
            }
            IconButton button = new IconButton(ignored -> selectPage(candidate)) {
                @Override protected Icon getIcon() { return pageIcon(candidate); }
            };
            button.setMessage(Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." + candidate.serializedName));
            pageButtons.put(candidate, button);
            addToLeftToolbar(button);
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
            case TRANSFER -> Icon.ACCESS_WRITE;
            case BREAK -> Icon.PLACEMENT_BLOCK;
            case REDSTONE -> Icon.REDSTONE_HIGH;
            case ENERGY -> Icon.POWER_UNIT_RF;
        };
    }

    protected final <B extends TabButton> B addToRightToolbar(String widgetId, B button) {
        button.setStyle(TabButton.Style.HORIZONTAL);
        widgets.add(widgetId, button);
        return button;
    }

}
