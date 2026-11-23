package org.ae2bc.client.screen.page;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.Pp2mpPagedScreen;

public final class Pp2mpPageContext {
    private final Pp2mpPagedScreen<?> screen;

    public Pp2mpPageContext(Pp2mpPagedScreen<?> screen) {
        this.screen = screen;
    }

    public AEBaseMenu getMenu() {
        return this.screen.getMenu();
    }

    public ScreenStyle getStyle() {
        return this.screen.getScreenStyle();
    }

    public int getLeftPos() {
        return this.screen.getScreenLeftPos();
    }

    public int getTopPos() {
        return this.screen.getScreenTopPos();
    }

    public AE2Button addButton(String id, Component message, Runnable onPress) {
        return this.screen.addButton(id, message, onPress);
    }

    public <W extends AbstractWidget> W addRenderableWidget(String id, W widget, Class<W> clazz) {
        return this.screen.addPageWidget(id, widget, clazz);
    }

    public <B extends IconButton> B addToRightToolbar(B button) {
        return screen.addToRightToolbar(button);
    }

    public int getImageWidth() {
        return screen.screenImageWidth();
    }

}
