package cn.ae2bc.client;

import appeng.api.util.AEColor;
import appeng.util.Platform;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComponentPlacerScreen extends ContainerScreen<ComponentPlacerMenu> {
    private static final ResourceLocation INVENTORY_TEXTURE =
            new ResourceLocation("textures/gui/container/inventory.png");
    private static final ResourceLocation UPGRADE_TEXTURE =
            new ResourceLocation("appliedenergistics2", "textures/guis/extra_panels.png");
    private final Map<Direction, Ae2Button> directionButtons =
            new EnumMap<Direction, Ae2Button>(Direction.class);
    private final Map<Button, ITextComponent> tooltips = new LinkedHashMap<Button, ITextComponent>();
    private final Button[] minus = new Button[3];
    private final Button[] plus = new Button[3];
    private Button reset;
    private Button clear;
    private Button execute;

    public ComponentPlacerScreen(ComponentPlacerMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 208;
        imageHeight = 228;
        inventoryLabelY = 134;
    }

    @Override
    protected void init() {
        super.init();
        directionButtons.clear();
        tooltips.clear();
        Direction front = minecraft.player.getDirection();
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        addDirection(106, 41, front, directionName(front));
        addDirection(72, 56, left, tr("gui.ae2_batchcraft.component_placer.relative.left", directionName(left)));
        addDirection(106, 56, Direction.UP, directionName(Direction.UP));
        addDirection(140, 56, right, tr("gui.ae2_batchcraft.component_placer.relative.right", directionName(right)));
        addDirection(72, 71, Direction.DOWN, directionName(Direction.DOWN));
        addDirection(106, 71, front.getOpposite(), directionName(front.getOpposite()));

        int[] y = { 41, 56, 71 };
        String[] axis = { "X", "Y", "Z" };
        for (int i = 0; i < 3; i++) {
            final int index = i;
            minus[i] = addButton(new Ae2Button(leftPos + 8, topPos + y[i], 14, 14,
                    new StringTextComponent("-"), button -> adjust(index, -1)));
            plus[i] = addButton(new Ae2Button(leftPos + 51, topPos + y[i], 14, 14,
                    new StringTextComponent("+"), button -> adjust(index, 1)));
            tooltips.put(minus[i], tr("gui.ae2_batchcraft.component_placer.offset.decrease.tooltip", axis[i]));
            tooltips.put(plus[i], tr("gui.ae2_batchcraft.component_placer.offset.increase.tooltip", axis[i]));
        }
        reset = addButton(new Ae2Button(leftPos + 8, topPos + 88, 52, 14,
                tr("gui.ae2_batchcraft.component_placer.reset_offsets"), button -> send(ModNetwork.PLACER_RESET_OFFSETS, 0)));
        clear = addButton(new Ae2Button(leftPos + 62, topPos + 88, 52, 14,
                tr("gui.ae2_batchcraft.component_placer.clear_selection"), button -> send(ModNetwork.PLACER_CLEAR_SELECTION, 0)));
        execute = addButton(new Ae2Button(leftPos + 116, topPos + 88, 52, 14,
                tr("gui.ae2_batchcraft.component_placer.execute"), button -> send(ModNetwork.PLACER_EXECUTE, 0)));
        tooltips.put(reset, tr("gui.ae2_batchcraft.component_placer.reset_offsets.tooltip"));
        tooltips.put(clear, tr("gui.ae2_batchcraft.component_placer.clear_selection.tooltip"));
    }

    private void addDirection(int x, int y, Direction direction, ITextComponent label) {
        Ae2Button button = addButton(new Ae2Button(leftPos + x, topPos + y, 32, 14, label,
                pressed -> send(ModNetwork.PLACER_SET_DIRECTION, direction.ordinal())));
        directionButtons.put(direction, button);
        tooltips.put(button, tr("gui.ae2_batchcraft.component_placer.direction.tooltip", label));
    }

    private void adjust(int axis, int delta) {
        send(ModNetwork.PLACER_ADJUST_X + axis, delta);
    }

    private void send(int action, int value) {
        ModNetwork.sendComponentPlacerAction(menu, action, value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= leftPos + 154 && mouseX < leftPos + 168
                && mouseY >= topPos + 22 && mouseY < topPos + 36) {
            if (button == 0) send(ModNetwork.PLACER_RESET_FREQUENCY, 0);
            else if (button == 1) send(ModNetwork.PLACER_LOAD_FREQUENCY, 0);
            else return false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateButtons() {
        for (Map.Entry<Direction, Ae2Button> entry : directionButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.getDirection();
        }
        int[] offsets = { menu.getOffsetX(), menu.getOffsetY(), menu.getOffsetZ() };
        for (int i = 0; i < 3; i++) {
            minus[i].active = offsets[i] > -ComponentPlacerSettings.MAX_OFFSET;
            plus[i].active = offsets[i] < ComponentPlacerSettings.MAX_OFFSET;
        }
        reset.active = menu.getOffsetX() != 0 || menu.getOffsetY() != 1 || menu.getOffsetZ() != 0;
        clear.active = menu.hasSelection();
        execute.active = menu.getSelectionState() == ComponentPlacerSelection.SelectionValidation.VALID
                && menu.hasCable() && menu.hasPart();
    }

    @Override
    protected void renderBg(MatrixStack matrices, float partialTick, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(matrices, leftPos, topPos, 176, imageHeight);
        drawSlot(matrices, 40, 22);
        drawSlot(matrices, 88, 22);
        for (int i = 0; i < 9; i++) drawSlot(matrices, 8 + i * 18, 115);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(matrices, 8 + column * 18, 146 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(matrices, 8 + column * 18, 204);
        drawUpgradePanel(matrices);
        drawFrequency(matrices);
    }

    private void drawSlot(MatrixStack matrices, int x, int y) {
        minecraft.getTextureManager().bind(INVENTORY_TEXTURE);
        blit(matrices, leftPos + x - 1, topPos + y - 1, 7, 83, 18, 18);
    }

    private void drawUpgradePanel(MatrixStack matrices) {
        minecraft.getTextureManager().bind(UPGRADE_TEXTURE);
        blit(matrices, leftPos + 176, topPos, 32, 25, 0, 0, 32, 25, 128, 128);
        blit(matrices, leftPos + 176, topPos + 25, 32, 18, 0, 7, 32, 18, 128, 128);
        blit(matrices, leftPos + 176, topPos + 43, 32, 25, 0, 7, 32, 25, 128, 128);
    }

    private void drawFrequency(MatrixStack matrices) {
        int x = leftPos + 154;
        int y = topPos + 22;
        fill(matrices, x, y, x + 14, y + 14, 0xFF8B8B8B);
        AEColor[] colors = Platform.p2p().toColors((short) menu.getFrequency());
        for (int i = 0; i < 4; i++) {
            int color = 0xFF000000 | colors[i].mediumVariant;
            fill(matrices, x + 1 + (i % 2) * 6, y + 1 + (i / 2) * 6,
                    x + 7 + (i % 2) * 6, y + 7 + (i / 2) * 6, color);
        }
    }

    @Override
    protected void renderLabels(MatrixStack matrices, int mouseX, int mouseY) {
        font.draw(matrices, title.getString(), 8, 6, 0x404040);
        fill(matrices, 7, 17, 169, 18, 0xFF808080);
        font.draw(matrices, tr("gui.ae2_batchcraft.component_placer.cable").getString(), 8, 25, 0x404040);
        font.draw(matrices, tr("gui.ae2_batchcraft.component_placer.part").getString(), 60, 25, 0x404040);
        String frequency = Platform.p2p().toHexString((short) menu.getFrequency());
        font.draw(matrices, frequency, 150 - font.width(frequency), 25, 0x404040);
        drawCentered(matrices, "X:" + menu.getOffsetX(), 36, 44);
        drawCentered(matrices, "Y:" + menu.getOffsetY(), 36, 59);
        drawCentered(matrices, "Z:" + menu.getOffsetZ(), 36, 74);
        font.draw(matrices, tr("gui.ae2_batchcraft.component_placer.materials").getString(), 8, 106, 0x404040);
        ITextComponent link = tr(menu.isAeConnected() ? "gui.ae2_batchcraft.component_placer.linked"
                : "gui.ae2_batchcraft.component_placer.not_linked");
        int color = menu.isAeConnected() ? 0x2F7D32 : 0xB03030;
        font.draw(matrices, link.getString(), 168 - font.width(link.getString()), 106, color);
        font.draw(matrices, tr("container.inventory").getString(), 8, 134, 0x404040);
    }

    private void drawCentered(MatrixStack matrices, String text, int centerX, int y) {
        font.draw(matrices, text, centerX - font.width(text) / 2.0F, y, 0x404040);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, partialTick);
        renderTooltip(matrices, mouseX, mouseY);
        if (mouseX >= leftPos + 154 && mouseX < leftPos + 168
                && mouseY >= topPos + 22 && mouseY < topPos + 36) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.component_placer.frequency.tooltip"), mouseX, mouseY);
        }
        if (insideSlot(mouseX, mouseY, 40, 22)) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.component_placer.cable.tooltip"), mouseX, mouseY);
        } else if (insideSlot(mouseX, mouseY, 88, 22)) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.component_placer.part.tooltip"), mouseX, mouseY);
        } else if (mouseX >= leftPos + 176 && mouseX < leftPos + 208
                && mouseY >= topPos && mouseY < topPos + 68) {
            renderTooltip(matrices, tr("gui.ae2_batchcraft.component_placer.upgrades.tooltip"), mouseX, mouseY);
        }
        for (Map.Entry<Button, ITextComponent> entry : tooltips.entrySet()) {
            if (entry.getKey().isHovered()) renderTooltip(matrices, entry.getValue(), mouseX, mouseY);
        }
        if (execute.isHovered()) {
            renderComponentTooltip(matrices, Arrays.asList(
                    tr("gui.ae2_batchcraft.component_placer.execute.tooltip"), selectionStatus()), mouseX, mouseY);
        }
    }

    private boolean insideSlot(int mouseX, int mouseY, int x, int y) {
        return mouseX >= leftPos + x - 1 && mouseX < leftPos + x + 17
                && mouseY >= topPos + y - 1 && mouseY < topPos + y + 17;
    }

    private ITextComponent selectionStatus() {
        switch (menu.getSelectionState()) {
            case VALID:
                return tr("gui.ae2_batchcraft.component_placer.selection.valid",
                        menu.getSizeX(), menu.getSizeY(), menu.getSizeZ());
            case VOLUME_NOT_ALLOWED:
                return tr("gui.ae2_batchcraft.component_placer.selection.volume");
            case TOO_LARGE:
                return tr("gui.ae2_batchcraft.component_placer.selection.too_large");
            default:
                return tr("gui.ae2_batchcraft.component_placer.selection.incomplete");
        }
    }

    private ITextComponent directionName(Direction direction) {
        return tr("gui.ae2_batchcraft.component_placer.direction." + direction.getName());
    }

    private static ITextComponent tr(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }
}
