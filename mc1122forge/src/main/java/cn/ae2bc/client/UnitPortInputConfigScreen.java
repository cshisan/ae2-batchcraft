package cn.ae2bc.client;

import appeng.api.AEApi;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;

/** AE2-styled legacy material return filter for input-type unit ports. */
public final class UnitPortInputConfigScreen extends GuiContainer {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final int MAIN_PANEL_WIDTH = 177;
    private static final int UPGRADE_PANEL_WIDTH = 35;
    private static final int MARKER_TOP = 36;
    private static final int PLAYER_INVENTORY_TOP = 84;
    private static final int HOTBAR_TOP = 142;
    private final UnitPortInputConfigMenu menu;

    public UnitPortInputConfigScreen(UnitPortInputConfigMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = MAIN_PANEL_WIDTH + UPGRADE_PANEL_WIDTH;
        ySize = 168;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(new Ae2Button(2, guiLeft + 152, guiTop - 5, 20, 20, "X"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) mc.player.closeScreen();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        if (isEmptyInverterSlot() && mouseX >= guiLeft + 187 && mouseX < guiLeft + 203
                && mouseY >= guiTop + 8 && mouseY < guiTop + 24) {
            drawHoveringText(Arrays.asList(
                    I18n.format("gui.ae2_batchcraft.compatible_upgrades"),
                    AEApi.instance().definitions().materials().cardInverter().maybeStack(1).get()
                            .getDisplayName()), mouseX, mouseY);
        }
    }

    private boolean isEmptyInverterSlot() {
        return menu.getPart() != null
                && menu.getPart().getInputFilterInverter().getStackInSlot(0).isEmpty();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(guiLeft, guiTop, MAIN_PANEL_WIDTH, ySize);
        drawRect(guiLeft + 7, guiTop + 17, guiLeft + MAIN_PANEL_WIDTH - 8,
                guiTop + 18, 0xFF808080);
        drawRect(guiLeft + 7, guiTop + 18, guiLeft + MAIN_PANEL_WIDTH - 8,
                guiTop + 19, 0xFFFFFFFF);
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        for (int i = 0; i < 18; i++) drawSlot(8 + i % 9 * 18, MARKER_TOP + i / 9 * 18);
        Ae2GuiSkin.drawInterfaceUpgradePanel(guiLeft, guiTop, MAIN_PANEL_WIDTH, 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(8 + col * 18, PLAYER_INVENTORY_TOP + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) drawSlot(8 + col * 18, HOTBAR_TOP);
    }

    private void drawSlot(int x, int y) {
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        drawTexturedModalRect(guiLeft + x - 1, guiTop + y - 1, 7, 83, 18, 18);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.unit_port_input_config.title"),
                8, 6, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.unit_port_input_config.markers"),
                8, 24, 0x404040);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, 73, 0x404040);
    }
}
