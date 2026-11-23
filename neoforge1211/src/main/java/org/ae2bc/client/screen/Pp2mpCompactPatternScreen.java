package org.ae2bc.client.screen;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;

public final class Pp2mpCompactPatternScreen extends Pp2mpPatternScreen {
    private final Blitter compactBackground;

    public Pp2mpCompactPatternScreen(Pp2mpEncodingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, Pp2mpCompactPatternScreen.withoutTerminalTitle(style));
        assert style.getTerminalStyle() != null;
        this.compactBackground = style.getTerminalStyle().getBottom();
    }

    private static appeng.client.gui.style.ScreenStyle withoutTerminalTitle(ScreenStyle style) {
        style.getText().remove("dialog_title");
        return style;
    }

    @Override
    public void init() {
        super.init();
        this.menu.slots.removeIf(slot -> this.menu.getSlotSemantic(slot) == null);
        this.hideSearchField();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.hideSearchField();
    }

    private void hideSearchField() {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AETextField searchField) {
                searchField.setVisible(false);
            }
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        this.compactBackground.dest(offsetX, offsetY).blit(graphics);
        this.drawEncodingBackground(graphics, offsetX, offsetY);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }
}
