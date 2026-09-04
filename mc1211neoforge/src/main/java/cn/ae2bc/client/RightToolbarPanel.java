package cn.ae2bc.client;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.widgets.IconButton;
import appeng.core.AppEng;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * AE2 upgrade-panel style container for actions attached to the right side of a screen.
 * The geometry intentionally follows UpgradesPanel so the panel uses the same continuous
 * outer frame instead of independently sliced button backgrounds.
 */
final class RightToolbarPanel implements ICompositeWidget {
    private static final int VERTICAL_SPACING = 6;
    private static final int TOP_PADDING = 3;
    private static final int BOTTOM_PADDING = 4;
    private static final int ICON_BUTTON_RENDER_EXTRA = 5;
    private static final int PANEL_WIDTH = 21;
    private static final int PANEL_X_OFFSET = 1;

    private final List<IconButton> buttons = new ArrayList<>();
    private Point screenOrigin = Point.ZERO;
    private Point position = Point.ZERO;

    void addButton(IconButton button) {
        buttons.add(button);
    }

    int getRequiredHeight() {
        return position.getY() + getPanelHeight();
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void setSize(int width, int height) {
        // The panel size is determined by the visible buttons, just like UpgradesPanel.
    }

    @Override
    public Rect2i getBounds() {
        int height = getPanelHeight();
        if (height == 0) {
            return new Rect2i(position.getX(), position.getY(), 0, 0);
        }
        return new Rect2i(position.getX() + PANEL_X_OFFSET - 1, position.getY(), PANEL_WIDTH, height);
    }

    @Override
    public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i screenBounds,
                               AEBaseScreen<?> screen) {
        screenOrigin = Point.fromTopLeft(screenBounds);
        for (IconButton button : buttons) {
            addWidget.accept(button);
        }
        updateBeforeRender();
    }

    @Override
    public void updateBeforeRender() {
        int y = position.getY() + TOP_PADDING;
        for (IconButton button : buttons) {
            if (!button.visible) {
                continue;
            }
            int x = position.getX();
            button.setX(screenOrigin.getX() + x + PANEL_X_OFFSET);
            button.setY(screenOrigin.getY() + y);
            y += button.getHeight() + VERTICAL_SPACING;
        }
    }

    @Override
    public void drawBackgroundLayer(GuiGraphics graphics, Rect2i screenBounds, Point origin) {
        int panelHeight = getPanelHeight();
        if (panelHeight > 0) {
            int x = screenOrigin.getX() + position.getX() + PANEL_X_OFFSET - 1;
            int y = screenOrigin.getY() + position.getY();
            graphics.blitSprite(AppEng.makeId("vertical_buttons_bg_right"),
                    x, y, PANEL_WIDTH, panelHeight);
        }
    }

    private int getPanelHeight() {
        int height = 0;
        int visible = 0;
        for (IconButton button : buttons) {
            if (!button.visible) {
                continue;
            }
            height += button.getHeight();
            if (visible++ > 0) {
                height += VERTICAL_SPACING;
            }
        }
        return visible == 0 ? 0
                : TOP_PADDING + height + ICON_BUTTON_RENDER_EXTRA + BOTTOM_PADDING;
    }
}
