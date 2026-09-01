package cn.ae2bc.client;

import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.network.ModNetwork;
import appeng.api.AEApi;
import appeng.client.gui.widgets.GuiNumberBox;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Arrays;

/** AE2-styled legacy output-port filter and priority screen. */
public final class UnitPortOutputConfigScreen extends GuiContainer {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final int MAIN_PANEL_WIDTH = 177;
    private static final int UPGRADE_PANEL_WIDTH = 35;
    private static final int MARKER_TOP = 102;
    private static final int PLAYER_INVENTORY_TOP = 150;
    private static final int HOTBAR_TOP = 208;
    private final UnitPortOutputConfigMenu menu;
    private GuiNumberBox priority;
    private Ae2IconButton singleSlotButton;

    public UnitPortOutputConfigScreen(UnitPortOutputConfigMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = MAIN_PANEL_WIDTH + UPGRADE_PANEL_WIDTH;
        ySize = 234;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        priority = new GuiNumberBox(fontRenderer, guiLeft + 60, guiTop + 56, 59,
                fontRenderer.FONT_HEIGHT, Long.class);
        priority.setEnableBackgroundDrawing(true);
        priority.setText(Integer.toString(menu.priority));
        priority.setMaxStringLength(16);
        priority.setTextColor(0xFFFFFF);
        priority.setVisible(true);
        priority.setFocused(true);
        addPriorityButtons();
        singleSlotButton = new Ae2IconButton(4, guiLeft - 24, guiTop + 19,
                menu.singleSlot ? Ae2IconButton.BLOCK_YES : Ae2IconButton.BLOCK_NO) {
            @Override
            public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY,
                                   float partialTicks) {
                setIconIndex(menu.singleSlot ? Ae2IconButton.BLOCK_YES : Ae2IconButton.BLOCK_NO);
                super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            }
        };
        // Keep the AE button background opaque even when the global mode locks this port.
        // The action handler below enforces the read-only state.
        singleSlotButton.enabled = true;
        buttonList.add(singleSlotButton);
        buttonList.add(new Ae2Button(2, guiLeft + 152, guiTop - 5, 20, 20, "X"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        try {
            ModNetwork.sendUnitPortPriority(menu, Integer.parseInt(priority.getText().trim()));
        } catch (NumberFormatException ignored) {
            // Keep the last valid server value when the field is incomplete.
        }
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.player.closeScreen();
        } else if (button.id == 4) {
            if (!menu.singleSlotEditable) return;
            menu.singleSlot = !menu.singleSlot;
            ModNetwork.sendUnitPortPriority(menu, parsePriority());
        } else if (button.id >= 10 && button.id < 18) {
            int[] steps = {1, 10, 100, 1000};
            int index = (button.id - 10) % 4;
            changePriority(button.id < 14 ? steps[index] : -steps[index]);
        }
    }

    private void addPriorityButtons() {
        int[] steps = {1, 10, 100, 1000};
        for (int i = 0; i < steps.length; i++) {
            buttonList.add(new Ae2Button(10 + i, guiLeft + 19 + i * 36, guiTop + 35, 32, 18, "+" + steps[i]));
            buttonList.add(new Ae2Button(14 + i, guiLeft + 19 + i * 36, guiTop + 68, 32, 18, "-" + steps[i]));
        }
    }

    private void changePriority(int delta) {
        int value = Math.max(PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY,
                Math.min(PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY, parsePriority() + delta));
        priority.setText(Integer.toString(value));
        ModNetwork.sendUnitPortPriority(menu, value);
    }

    private void submitPriorityAndClose() {
        try {
            ModNetwork.sendUnitPortPriority(menu, Integer.parseInt(priority.getText().trim()));
        } catch (NumberFormatException ignored) {
            priority.setFocused(true);
            return;
        }
        mc.player.closeScreen();
    }
    private int parsePriority() {
        try { return Integer.parseInt(priority.getText().trim()); }
        catch (NumberFormatException ignored) { return menu.priority; }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (priority.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        priority.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (singleSlotButton != null) {
            singleSlotButton.enabled = true;
            singleSlotButton.setIconIndex(menu.singleSlot ? Ae2IconButton.BLOCK_YES : Ae2IconButton.BLOCK_NO);
        }
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        priority.drawTextBox();
        renderHoveredToolTip(mouseX, mouseY);
        if (singleSlotButton != null && singleSlotButton.isHovered()) {
            drawHoveringText(Arrays.asList(I18n.format(
                    "gui.ae2_batchcraft.unit_port_output_config.single_slot.tooltip",
                    I18n.format(menu.singleSlot
                            ? "gui.ae2_batchcraft.enabled" : "gui.ae2_batchcraft.disabled")).replace("\\n", "\n").split("\n", -1)),
                    mouseX, mouseY);
        }
        if (isEmptyInverterSlot() && mouseX >= guiLeft + 187 && mouseX < guiLeft + 203
                && mouseY >= guiTop + 8 && mouseY < guiTop + 24) {
            drawHoveringText(Arrays.asList(
                    I18n.format("gui.ae2_batchcraft.compatible_upgrades"),
                    AEApi.instance().definitions().materials().cardInverter().maybeStack(1).get()
                            .getDisplayName()), mouseX, mouseY);
        }
    }

    private boolean isEmptyInverterSlot() {
        return menu.getPart() != null && menu.getPart().getOutputFilterInverter().getStackInSlot(0).isEmpty();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(guiLeft, guiTop, MAIN_PANEL_WIDTH, ySize);
        drawRect(guiLeft + 7, guiTop + 17, guiLeft + MAIN_PANEL_WIDTH - 8, guiTop + 18, 0xFF808080);
        drawRect(guiLeft + 7, guiTop + 18, guiLeft + MAIN_PANEL_WIDTH - 8, guiTop + 19, 0xFFFFFFFF);
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        for (int i = 0; i < 18; i++) drawSlot(8 + i % 9 * 18, MARKER_TOP + i / 9 * 18);
        Ae2GuiSkin.drawInterfaceUpgradePanel(guiLeft, guiTop, MAIN_PANEL_WIDTH, 1);
        for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) drawSlot(8 + col * 18, PLAYER_INVENTORY_TOP + row * 18);
        }
        for (int col = 0; col < 9; col++) drawSlot(8 + col * 18, HOTBAR_TOP);
    }

    private void drawSlot(int x, int y) {
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        drawTexturedModalRect(guiLeft + x - 1, guiTop + y - 1, 7, 83, 18, 18);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.unit_port_output_config.title"),
                8, 6, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.priority"), 8, 24, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.unit_port_output_config.markers"),
                8, 89, 0x404040);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, 139, 0x404040);
    }
}
