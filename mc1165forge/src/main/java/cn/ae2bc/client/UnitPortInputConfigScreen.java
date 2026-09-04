package cn.ae2bc.client;

import appeng.client.Point;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.Api;
import appeng.core.localization.GuiText;
import cn.ae2bc.menu.UnitPortInputConfigMenu;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.Arrays;

/** AE2-style material return filter screen for input-type unit ports. */
public final class UnitPortInputConfigScreen extends ContainerScreen<UnitPortInputConfigMenu> {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final int MAIN_PANEL_WIDTH = 176;
    private static final int UPGRADE_PANEL_WIDTH = 32;
    private UpgradesPanel upgradesPanel;

    public UnitPortInputConfigScreen(UnitPortInputConfigMenu menu, PlayerInventory inventory,
            ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = MAIN_PANEL_WIDTH + UPGRADE_PANEL_WIDTH;
        imageHeight = 168;
    }

    @Override
    protected void init() {
        super.init();
        upgradesPanel = new UpgradesPanel(menu.getUpgradeSlots());
        upgradesPanel.populateScreen(ignored -> { },
                new Rectangle2d(leftPos, topPos, imageWidth, imageHeight), null);
        upgradesPanel.setPosition(new Point(MAIN_PANEL_WIDTH, 0));
        upgradesPanel.updateBeforeRender();
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        renderTooltip(stack, mouseX, mouseY);
        if (isEmptyInverterSlot() && mouseX >= leftPos + 184 && mouseX < leftPos + 200
                && mouseY >= topPos + 8 && mouseY < topPos + 24) {
            renderComponentTooltip(stack, Arrays.asList(
                    GuiText.CompatibleUpgrades.text(),
                    Api.instance().definitions().materials().cardInverter().maybeStack(1).get().getHoverName()),
                    mouseX, mouseY);
        }
    }

    private boolean isEmptyInverterSlot() {
        return menu.getPart() != null
                && menu.getPart().getInputFilterInverter().getStackInSlot(0).isEmpty();
    }

    @Override
    protected void renderLabels(MatrixStack stack, int mouseX, int mouseY) {
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent(
                "gui.ae2_batchcraft.unit_port_input_config.title"), 8, 6, 0x404040);
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent(
                "gui.ae2_batchcraft.unit_port_input_config.markers"), 8, 24, 0x404040);
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent(
                "container.inventory"), 8, 73, 0x404040);
    }

    @Override
    protected void renderBg(MatrixStack stack, float partialTicks, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(stack, leftPos, topPos, MAIN_PANEL_WIDTH, imageHeight);
        fill(stack, leftPos + 7, topPos + 17, leftPos + MAIN_PANEL_WIDTH - 8,
                topPos + 18, 0xFF808080);
        fill(stack, leftPos + 7, topPos + 18, leftPos + MAIN_PANEL_WIDTH - 8,
                topPos + 19, 0xFFFFFFFF);
        for (int i = 0; i < 18; i++) drawSlot(stack, 8 + (i % 9) * 18, 36 + (i / 9) * 18);
        if (upgradesPanel != null) {
            upgradesPanel.drawBackgroundLayer(stack, getBlitOffset(), upgradesPanel.getBounds(), Point.ZERO);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) drawSlot(stack, 8 + col * 18, 84 + row * 18);
        }
        for (int col = 0; col < 9; col++) drawSlot(stack, 8 + col * 18, 142);
    }

    private void drawSlot(MatrixStack stack, int x, int y) {
        minecraft.getTextureManager().bind(INVENTORY_TEXTURE);
        blit(stack, leftPos + x - 1, topPos + y - 1, 7, 83, 18, 18);
    }
}
