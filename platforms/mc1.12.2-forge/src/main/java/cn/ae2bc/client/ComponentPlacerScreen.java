package cn.ae2bc.client;

import appeng.api.util.AEColor;
import appeng.util.Platform;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ComponentPlacerScreen extends GuiContainer {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final int UPGRADE_PANEL_HEIGHT = 32;
    private final ComponentPlacerMenu menu;
    private final Map<EnumFacing, GuiButton> directionButtons = new EnumMap<EnumFacing, GuiButton>(EnumFacing.class);
    private final Map<Integer, String> tooltips = new HashMap<Integer, String>();
    private final GuiButton[] minus = new GuiButton[3];
    private final GuiButton[] plus = new GuiButton[3];
    private GuiButton reset;
    private GuiButton clear;
    private GuiButton execute;

    public ComponentPlacerScreen(ComponentPlacerMenu menu, InventoryPlayer inventory) {
        super(menu); this.menu = menu; xSize = 212; ySize = 228;
    }

    @Override public void initGui() {
        super.initGui(); directionButtons.clear(); tooltips.clear(); buttonList.clear();
        EnumFacing front = mc.player.getHorizontalFacing();
        EnumFacing left = front.rotateYCCW(), right = front.rotateY();
        addDirection(100, 106, 41, front, directionName(front));
        addDirection(101, 72, 56, left, I18n.format("gui.ae2_batchcraft.component_placer.relative.left", directionName(left)));
        addDirection(102, 106, 56, EnumFacing.UP, directionName(EnumFacing.UP));
        addDirection(103, 140, 56, right, I18n.format("gui.ae2_batchcraft.component_placer.relative.right", directionName(right)));
        addDirection(104, 72, 71, EnumFacing.DOWN, directionName(EnumFacing.DOWN));
        addDirection(105, 106, 71, front.getOpposite(), directionName(front.getOpposite()));
        int[] ys = { 41, 56, 71 };
        for (int i = 0; i < 3; i++) {
            minus[i] = add(new Ae2Button(10 + i * 2, guiLeft + 8, guiTop + ys[i], 14, 14, "-"));
            plus[i] = add(new Ae2Button(11 + i * 2, guiLeft + 51, guiTop + ys[i], 14, 14, "+"));
            tooltips.put(minus[i].id, I18n.format("gui.ae2_batchcraft.component_placer.offset.decrease.tooltip", "XYZ".substring(i, i + 1)));
            tooltips.put(plus[i].id, I18n.format("gui.ae2_batchcraft.component_placer.offset.increase.tooltip", "XYZ".substring(i, i + 1)));
        }
        reset = add(new Ae2Button(20, guiLeft + 8, guiTop + 88, 52, 14,
                I18n.format("gui.ae2_batchcraft.component_placer.reset_offsets")));
        clear = add(new Ae2Button(21, guiLeft + 62, guiTop + 88, 52, 14,
                I18n.format("gui.ae2_batchcraft.component_placer.clear_selection")));
        execute = add(new Ae2Button(22, guiLeft + 116, guiTop + 88, 52, 14,
                I18n.format("gui.ae2_batchcraft.component_placer.execute")));
        tooltips.put(20, I18n.format("gui.ae2_batchcraft.component_placer.reset_offsets.tooltip"));
        tooltips.put(21, I18n.format("gui.ae2_batchcraft.component_placer.clear_selection.tooltip"));
    }

    private GuiButton add(GuiButton button) { buttonList.add(button); return button; }
    private void addDirection(int id, int x, int y, EnumFacing direction, String text) {
        GuiButton button = add(new Ae2Button(id, guiLeft + x, guiTop + y, 32, 14, text));
        directionButtons.put(direction, button);
        tooltips.put(id, I18n.format("gui.ae2_batchcraft.component_placer.direction.tooltip", text));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id >= 100 && button.id <= 105) {
            for (Map.Entry<EnumFacing, GuiButton> entry : directionButtons.entrySet())
                if (entry.getValue() == button) send(ModNetwork.PLACER_SET_DIRECTION, entry.getKey().ordinal());
        } else if (button.id >= 10 && button.id <= 15) {
            int axis = (button.id - 10) / 2; int delta = button.id % 2 == 0 ? -1 : 1;
            send(ModNetwork.PLACER_ADJUST_X + axis, delta);
        } else if (button.id == 20) send(ModNetwork.PLACER_RESET_OFFSETS, 0);
        else if (button.id == 21) send(ModNetwork.PLACER_CLEAR_SELECTION, 0);
        else if (button.id == 22) send(ModNetwork.PLACER_EXECUTE, 0);
    }
    private void send(int action, int value) { ModNetwork.sendComponentPlacerAction(menu, action, value); }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseX >= guiLeft + 154 && mouseX < guiLeft + 168 && mouseY >= guiTop + 22 && mouseY < guiTop + 36) {
            if (mouseButton == 0) send(ModNetwork.PLACER_RESET_FREQUENCY, 0);
            else if (mouseButton == 1) send(ModNetwork.PLACER_LOAD_FREQUENCY, 0);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void updateButtons() {
        for (Map.Entry<EnumFacing, GuiButton> entry : directionButtons.entrySet()) {
            entry.getValue().enabled = true;
            entry.getValue().enabled = entry.getKey() != menu.getDirection();
        }
        int[] offsets = { menu.getOffsetX(), menu.getOffsetY(), menu.getOffsetZ() };
        for (int i = 0; i < 3; i++) { minus[i].enabled = offsets[i] > -16; plus[i].enabled = offsets[i] < 16; }
        reset.enabled = menu.getOffsetX() != 0 || menu.getOffsetY() != 1 || menu.getOffsetZ() != 0;
        clear.enabled = menu.hasSelection();
        execute.enabled = menu.getSelectionState() == ComponentPlacerSelection.SelectionValidation.VALID
                && menu.hasCable() && menu.hasPart();
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(guiLeft, guiTop, 177, ySize);
        drawSlot(40, 22); drawSlot(88, 22);
        for (int i = 0; i < 9; i++) drawSlot(8 + i * 18, 115);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) drawSlot(8 + column * 18, 146 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(8 + column * 18, 204);
        Ae2GuiSkin.drawInterfaceUpgradePanel(guiLeft, guiTop, 177, 1);
        drawFrequency();
    }
    private void drawSlot(int x, int y) {
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        drawTexturedModalRect(guiLeft + x - 1, guiTop + y - 1, 7, 83, 18, 18);
    }
    private void drawFrequency() {
        int x = guiLeft + 154, y = guiTop + 22; drawRect(x, y, x + 14, y + 14, 0xFF8B8B8B);
        AEColor[] colors = Platform.p2p().toColors((short) menu.getFrequency());
        for (int i = 0; i < 4; i++) drawRect(x + 1 + i % 2 * 6, y + 1 + i / 2 * 6,
                x + 7 + i % 2 * 6, y + 7 + i / 2 * 6, 0xFF000000 | colors[i].mediumVariant);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("item.ae2_batchcraft.component_placer.name"), 8, 6, 0x404040);
        drawRect(7, 17, 169, 18, 0xFF808080);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.component_placer.cable"), 8, 25, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.component_placer.part"), 60, 25, 0x404040);
        String frequency = Platform.p2p().toHexString((short) menu.getFrequency());
        fontRenderer.drawString(frequency, 150 - fontRenderer.getStringWidth(frequency), 25, 0x404040);
        center("X:" + menu.getOffsetX(), 36, 44); center("Y:" + menu.getOffsetY(), 36, 59); center("Z:" + menu.getOffsetZ(), 36, 74);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.component_placer.materials"), 8, 106, 0x404040);
        String link = I18n.format(menu.isAeConnected() ? "gui.ae2_batchcraft.component_placer.linked"
                : "gui.ae2_batchcraft.component_placer.not_linked");
        fontRenderer.drawString(link, 168 - fontRenderer.getStringWidth(link), 106,
                menu.isAeConnected() ? 0x2F7D32 : 0xB03030);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, 134, 0x404040);
    }
    private void center(String text, int x, int y) { fontRenderer.drawString(text, x - fontRenderer.getStringWidth(text) / 2, y, 0x404040); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateButtons(); drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks); renderHoveredToolTip(mouseX, mouseY);
        if (mouseX >= guiLeft + 154 && mouseX < guiLeft + 168 && mouseY >= guiTop + 22 && mouseY < guiTop + 36)
            drawHoveringText(I18n.format("gui.ae2_batchcraft.component_placer.frequency.tooltip"), mouseX, mouseY);
        if (insideSlot(mouseX, mouseY, 40, 22))
            drawHoveringText(I18n.format("gui.ae2_batchcraft.component_placer.cable.tooltip"), mouseX, mouseY);
        else if (insideSlot(mouseX, mouseY, 88, 22))
            drawHoveringText(I18n.format("gui.ae2_batchcraft.component_placer.part.tooltip"), mouseX, mouseY);
        else if (mouseX >= guiLeft + 177 && mouseX < guiLeft + 212
                && mouseY >= guiTop && mouseY < guiTop + UPGRADE_PANEL_HEIGHT)
            drawHoveringText(I18n.format("gui.ae2_batchcraft.component_placer.upgrades.tooltip"), mouseX, mouseY);
        for (GuiButton button : buttonList) if (button.isMouseOver() && tooltips.containsKey(button.id))
            drawHoveringText(tooltips.get(button.id), mouseX, mouseY);
        if (execute.isMouseOver()) drawHoveringText(Arrays.asList(
                I18n.format("gui.ae2_batchcraft.component_placer.execute.tooltip"), selectionStatus()), mouseX, mouseY);
    }
    private boolean insideSlot(int mouseX, int mouseY, int x, int y) {
        return mouseX >= guiLeft + x - 1 && mouseX < guiLeft + x + 17
                && mouseY >= guiTop + y - 1 && mouseY < guiTop + y + 17;
    }
    private String selectionStatus() {
        switch (menu.getSelectionState()) {
            case VALID: return I18n.format("gui.ae2_batchcraft.component_placer.selection.valid", menu.getSizeX(), menu.getSizeY(), menu.getSizeZ());
            case VOLUME_NOT_ALLOWED: return I18n.format("gui.ae2_batchcraft.component_placer.selection.volume");
            case TOO_LARGE: return I18n.format("gui.ae2_batchcraft.component_placer.selection.too_large");
            default: return I18n.format("gui.ae2_batchcraft.component_placer.selection.incomplete");
        }
    }
    private static String directionName(EnumFacing direction) {
        return I18n.format("gui.ae2_batchcraft.component_placer.direction." + direction.getName());
    }
}
