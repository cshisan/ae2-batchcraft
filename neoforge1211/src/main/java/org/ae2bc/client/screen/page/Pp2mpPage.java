package org.ae2bc.client.screen.page;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.ae2bc.core.screen.Route;

public interface Pp2mpPage {
    Route getRoute();

    int height();

    void init(Pp2mpPageContext context);

    void setVisible(boolean visible);

    void layout();

    void update();

    void drawBackground(
            GuiGraphics graphics, Font font, int offsetX, int offsetY
    );

    void drawForeground(
            GuiGraphics graphics, Font font, int offsetX, int offsetY
    );

    default void commit() {
    }

    ;
}
