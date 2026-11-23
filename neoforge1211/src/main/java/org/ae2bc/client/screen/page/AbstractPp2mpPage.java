package org.ae2bc.client.screen.page;

import appeng.client.Point;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IResizableWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.core.screen.Route;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPp2mpPage implements Pp2mpPage {
    private final Route route;
    private final int pageHeight;
    private final List<FeatureComponent> sections;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    protected Pp2mpPageContext context;

    protected AbstractPp2mpPage(Route route, int pageHeight, FeatureComponent... sections) {
        this.route = route;
        this.pageHeight = pageHeight;
        this.sections = List.of(sections);
    }

    @Override
    public final Route getRoute() {
        return this.route;
    }

    @Override
    public final int height() {
        return this.pageHeight;
    }

    @Override
    public void init(Pp2mpPageContext context) {
        this.context = context;
    }

    @Override
    public void setVisible(boolean visible) {
        for (AbstractWidget widget : this.widgets) {
            widget.visible = visible;
        }
    }

    protected final AE2Button addButton(String id, Component message, Runnable onPress) {
        AE2Button button = this.context.addButton(id, message, onPress);
        this.widgets.add(button);
        return button;
    }

    /* 创建带完整说明的短文案按钮。 */
    protected final AE2Button addButton(
            String id, Component message, Component tooltip, Runnable onPress
    ) {
        AE2Button button = this.addButton(id, message, onPress);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    protected final AE2Button addButton(String id, Component message) {
        return this.addButton(id, message, () -> {
        });
    }

    protected final <W extends AbstractWidget> W addWidget(String id, W widget, Class<W> clazz) {
        W result = context.addRenderableWidget(id, widget, clazz);
        this.widgets.add(result);
        return result;
    }

    protected final void setWidgetBounds(AbstractWidget widget, int relativeX, int relativeY, int width, int height) {
        this.positionWidget(widget, context.getLeftPos() + relativeX, context.getTopPos() + relativeY, width, height);
    }

    /* 单元分页的模式按钮使用相同的三列布局。 */
    protected final void layoutModeButtons(AbstractWidget[] buttons) {
        for (int index = 0; index < buttons.length; index++) {
            this.setWidgetBounds(buttons[index], 12 + index * 51, 50, 46, 18);
        }
    }

    protected final void positionWidget(AbstractWidget widget, int x, int y, int width, int height) {
        if (widget instanceof IResizableWidget resizableWidget) {
            resizableWidget.resize(width, height);
            resizableWidget.move(new Point(x, y));
        } else {
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(width);
            widget.setHeight(height);
        }
    }

    @Override
    public final void drawBackground(
            GuiGraphics graphics, Font font, int offsetX, int offsetY
    ) {
        sections.forEach(section -> section.drawBackground(
                graphics, font, offsetX, offsetY, context.getImageWidth()
        ));
    }

    @Override
    public final void drawForeground(GuiGraphics graphics, Font font, int offsetX, int offsetY) {
        sections.forEach(section -> section.drawForeground(
                graphics, font, offsetX, offsetY, context.getImageWidth()
        ));
    }

    @Override
    public void update() {
    }
}
