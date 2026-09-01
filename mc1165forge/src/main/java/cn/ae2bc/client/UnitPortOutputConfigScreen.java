package cn.ae2bc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import appeng.api.config.Upgrades;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.Api;
import appeng.core.localization.GuiText;
import cn.ae2bc.menu.UnitPortOutputConfigMenu;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.Rectangle2d;
import cn.ae2bc.network.ModNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.text.TranslationTextComponent;

public final class UnitPortOutputConfigScreen extends ContainerScreen<UnitPortOutputConfigMenu> {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final int MAIN_PANEL_WIDTH = 176;
    private static final int UPGRADE_PANEL_WIDTH = 32;
    private TextFieldWidget priority;
    private DisabledStateAe2IconButton singleSlotButton;
    private UpgradesPanel upgradesPanel;
    public UnitPortOutputConfigScreen(UnitPortOutputConfigMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = MAIN_PANEL_WIDTH + UPGRADE_PANEL_WIDTH;
        imageHeight = 242;
    }
    @Override protected void init() {
        super.init();
        upgradesPanel = new UpgradesPanel(menu.getUpgradeSlots());
        upgradesPanel.populateScreen(ignored -> { },
                new Rectangle2d(leftPos, topPos, imageWidth, imageHeight), null);
        upgradesPanel.setPosition(new Point(MAIN_PANEL_WIDTH, 0));
        upgradesPanel.updateBeforeRender();
        priority = new TextFieldWidget(font, leftPos + 58, topPos + 58, 61, 16, new net.minecraft.util.text.StringTextComponent(""));
        priority.setValue(Integer.toString(menu.priority));
        priority.setFilter(text -> {
            if (text.isEmpty() || "-".equals(text)) return true;
            try {
                int value = Integer.parseInt(text);
                return value >= PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY
                        && value <= PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY;
            } catch (NumberFormatException ignored) {
                return false;
            }
        });
        priority.setResponder(text -> {
            if (!text.isEmpty() && !"-".equals(text)) {
                ModNetwork.sendUnitPortPriority(menu, parsePriority());
            }
        });
        addWidget(priority);
        addPriorityButtons();
        singleSlotButton = addButton(new DisabledStateAe2IconButton(leftPos - 24, topPos + 19, 20,
                button -> {
                    if (!menu.getSyncedSingleSlotEditable()) return;
                    // Keep the visual state authoritative: it changes only after the
                    // server accepts the request and synchronizes the container data.
                    ModNetwork.sendUnitPortPriority(menu, parsePriority(),
                            !menu.getSyncedSingleSlot());
                }));
        singleSlotButton.active = menu.getSyncedSingleSlotEditable();
    }
    @Override public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        if (singleSlotButton != null) {
            boolean syncedSingleSlot = menu.getSyncedSingleSlot();
            boolean syncedEditable = menu.getSyncedSingleSlotEditable();
            menu.singleSlot = syncedSingleSlot;
            menu.singleSlotEditable = syncedEditable;
            singleSlotButton.active = syncedEditable;
            singleSlotButton.setIconIndex(syncedSingleSlot ? 21 : 20);
        }
        renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        priority.render(stack, mouseX, mouseY, partialTicks);
        renderTooltip(stack, mouseX, mouseY);
        if (singleSlotButton != null && singleSlotButton.isHovered()) {
            renderComponentTooltip(stack, singleSlotTooltip(), mouseX, mouseY);
        }
        if (isEmptyInverterSlot() && mouseX >= leftPos + 184 && mouseX < leftPos + 200
                && mouseY >= topPos + 8 && mouseY < topPos + 24) {
            renderComponentTooltip(stack, Arrays.asList(
                    GuiText.CompatibleUpgrades.text(),
                    Api.instance().definitions().materials().cardInverter().maybeStack(1).get().getHoverName()),
                    mouseX, mouseY);
        }
    }

    private boolean isEmptyInverterSlot() {
        return menu.getPart() != null && menu.getPart().getOutputFilterInverter().getStackInSlot(0).isEmpty();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ContainerScreen's slot hit-testing can consume clicks outside the main panel.
        if (singleSlotButton != null && menu.getSyncedSingleSlotEditable()
                && singleSlotButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public void onClose() {
        try {
            ModNetwork.sendUnitPortPriority(menu, Integer.parseInt(priority.getValue().trim()));
        } catch (NumberFormatException ignored) {
            // Keep the last valid server value when the field is incomplete.
        }
        super.onClose();
    }
    private void submitPriorityAndClose() {
        try {
            ModNetwork.sendUnitPortPriority(menu, Integer.parseInt(priority.getValue().trim()));
            minecraft.player.closeContainer();
        } catch (NumberFormatException ignored) {
            priority.setFocus(true);
        }
    }

    private void addPriorityButtons() {
        int[] steps = {1, 10, 100, 1000};
        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            int x = 19 + i * 36;
            addButton(new Ae2Button(leftPos + x, topPos + 35, 32, 18,
                    new net.minecraft.util.text.StringTextComponent("+" + step), button -> changePriority(step)));
            addButton(new Ae2Button(leftPos + x, topPos + 77, 32, 18,
                    new net.minecraft.util.text.StringTextComponent("-" + step), button -> changePriority(-step)));
        }
    }

    private void changePriority(int delta) {
        int value = Math.max(PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY,
                Math.min(PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY, parsePriority() + delta));
        priority.setValue(Integer.toString(value));
        ModNetwork.sendUnitPortPriority(menu, value);
    }
    private int parsePriority() {
        try { return Integer.parseInt(priority.getValue().trim()); }
        catch (NumberFormatException ignored) { return menu.priority; }
    }
    private List<ITextComponent> singleSlotTooltip() {
        ITextComponent state = new TranslationTextComponent(menu.singleSlot
                ? "gui.ae2_batchcraft.enabled" : "gui.ae2_batchcraft.disabled");
        String text = new TranslationTextComponent(
                "gui.ae2_batchcraft.unit_port_output_config.single_slot.tooltip", state).getString();
        List<ITextComponent> lines = new ArrayList<>();
        for (String line : text.replace("\\n", "\n").split("\n", -1)) {
            lines.add(new net.minecraft.util.text.StringTextComponent(line));
        }
        return lines;
    }
    @Override protected void renderLabels(MatrixStack stack, int mouseX, int mouseY) {
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent(
                "gui.ae2_batchcraft.unit_port_output_config.title"), 8, 6, 0x404040);
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent("gui.ae2_batchcraft.priority"), 8, 24, 0x404040);
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent("gui.ae2_batchcraft.unit_port_output_config.markers"), 8, 97, 0x404040);
        font.draw(stack, new net.minecraft.util.text.TranslationTextComponent("container.inventory"), 8, 147, 0x404040);
    }
    @Override protected void renderBg(MatrixStack stack, float partialTicks, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(stack, leftPos, topPos, MAIN_PANEL_WIDTH, imageHeight);
        fill(stack, leftPos + 7, topPos + 17, leftPos + MAIN_PANEL_WIDTH - 8, topPos + 18, 0xFF808080);
        fill(stack, leftPos + 7, topPos + 18, leftPos + MAIN_PANEL_WIDTH - 8, topPos + 19, 0xFFFFFFFF);
        for (int i = 0; i < 18; i++) drawSlot(stack, 8 + (i % 9) * 18, 110 + (i / 9) * 18);
        if (upgradesPanel != null) {
            upgradesPanel.drawBackgroundLayer(stack, getBlitOffset(), upgradesPanel.getBounds(), Point.ZERO);
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) drawSlot(stack, 8 + col * 18, 158 + row * 18);
        for (int col = 0; col < 9; col++) drawSlot(stack, 8 + col * 18, 216);
    }
    private void drawSlot(MatrixStack stack, int x, int y) {
        minecraft.getTextureManager().bind(INVENTORY_TEXTURE);
        blit(stack, leftPos + x - 1, topPos + y - 1, 7, 83, 18, 18);
    }

}
