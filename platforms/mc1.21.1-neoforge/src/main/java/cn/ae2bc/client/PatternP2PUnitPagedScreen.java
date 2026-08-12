package cn.ae2bc.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

abstract class PatternP2PUnitPagedScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    private static final int LEFT_TOOLBAR_MIN_HEIGHT = 55;

    protected enum Page {
        COMMON("common"),
        BREAK("break"),
        REDSTONE("redstone");

        private final String serializedName;

        Page(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    private final IconButton pageButton;
    private Page page = Page.COMMON;

    protected PatternP2PUnitPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);

        var closeButton = new TabButton(Icon.CLEAR,
                Component.translatable("gui.ae2_batchcraft.configuration.close"), button -> onClose());
        widgets.add("close", closeButton);

        pageButton = new IconButton(button -> selectNextPage()) {
            @Override
            protected Icon getIcon() {
                return Icon.ARROW_RIGHT;
            }
        };
        pageButton.setMessage(pageButtonMessage());
        addToLeftToolbar(pageButton);
    }

    @Override
    protected void init() {
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
        pageButton.setMessage(pageButtonMessage());
        setTextContent("page_title", Component.translatable(
                "gui.ae2_batchcraft.pattern_p2p_unit.page." + page.serializedName));
    }

    protected final boolean isPage(Page candidate) {
        return page == candidate;
    }

    protected abstract int getPageHeight(Page candidate);

    protected abstract void updatePageVisibility();

    private void selectNextPage() {
        page = switch (page) {
            case COMMON -> Page.BREAK;
            case BREAK -> Page.REDSTONE;
            case REDSTONE -> Page.COMMON;
        };
        imageHeight = resolvePageHeight(page);
        updatePageVisibility();
    }

    private int resolvePageHeight(Page candidate) {
        return Math.max(getPageHeight(candidate), LEFT_TOOLBAR_MIN_HEIGHT);
    }

    private Component pageButtonMessage() {
        Page nextPage = switch (page) {
            case COMMON -> Page.BREAK;
            case BREAK -> Page.REDSTONE;
            case REDSTONE -> Page.COMMON;
        };
        return Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page.switch",
                Component.translatable("gui.ae2_batchcraft.pattern_p2p_unit.page." + nextPage.serializedName));
    }
}
