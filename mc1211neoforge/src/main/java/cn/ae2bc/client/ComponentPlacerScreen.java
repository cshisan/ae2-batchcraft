package cn.ae2bc.client;

import com.mojang.blaze3d.systems.RenderSystem;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.util.Platform;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class ComponentPlacerScreen extends AEBaseScreen<ComponentPlacerMenu> {
    private final Map<Direction, AE2Button> directionButtons = new EnumMap<>(Direction.class);
    private final AE2Button[] minusButtons = new AE2Button[3];
    private final AE2Button[] plusButtons = new AE2Button[3];
    private final AE2Button resetOffsets;
    private final AE2Button clearSelection;
    private final AE2Button execute;
    private final FrequencyWidget frequencyWidget;

    public ComponentPlacerScreen(ComponentPlacerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        frequencyWidget = new FrequencyWidget(() -> menu.frequency);
        frequencyWidget.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.frequency.tooltip")));
        widgets.add("frequency", frequencyWidget);
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                this::getCompatibleUpgrades));

        Direction front = playerInventory.player.getDirection();
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        addDirectionButton("directionFront", front, directionName(front));
        addDirectionButton("directionLeft", left, Component.translatable(
                "gui.ae2_batchcraft.component_placer.relative.left", directionName(left)));
        addDirectionButton("directionUp", Direction.UP, directionName(Direction.UP));
        addDirectionButton("directionRight", right, Component.translatable(
                "gui.ae2_batchcraft.component_placer.relative.right", directionName(right)));
        addDirectionButton("directionDown", Direction.DOWN, directionName(Direction.DOWN));
        addDirectionButton("directionBack", front.getOpposite(), directionName(front.getOpposite()));

        String[] axes = { "x", "y", "z" };
        for (int i = 0; i < axes.length; i++) {
            int axis = i;
            minusButtons[i] = addCompactButton("offset" + capitalize(axes[i]) + "Minus", Component.literal("-"),
                    () -> menu.adjustOffset(axis, -1));
            minusButtons[i].setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.component_placer.offset.decrease.tooltip",
                    axes[i].toUpperCase(java.util.Locale.ROOT))));
            plusButtons[i] = addCompactButton("offset" + capitalize(axes[i]) + "Plus", Component.literal("+"),
                    () -> menu.adjustOffset(axis, 1));
            plusButtons[i].setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.component_placer.offset.increase.tooltip",
                    axes[i].toUpperCase(java.util.Locale.ROOT))));
        }

        resetOffsets = addCompactButton("resetOffsets",
                Component.translatable("gui.ae2_batchcraft.component_placer.reset_offsets"), menu::resetOffsets);
        resetOffsets.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.reset_offsets.tooltip")));
        clearSelection = addCompactButton("clearSelection",
                Component.translatable("gui.ae2_batchcraft.component_placer.clear_selection"), menu::clearSelection);
        clearSelection.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.clear_selection.tooltip")));
        execute = addCompactButton("execute",
                Component.translatable("gui.ae2_batchcraft.component_placer.execute"), menu::execute);
        execute.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.execute.tooltip")));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (frequencyWidget.active && frequencyWidget.visible && frequencyWidget.isMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                menu.resetFrequency();
                return true;
            }
            if (button == 1) {
                menu.loadFrequency();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
                       float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, ComponentPlacerMenu.CABLE_MARKER_SLOT);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, ComponentPlacerMenu.PART_MARKER_SLOT);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, SlotSemantics.STORAGE);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, SlotSemantics.PLAYER_INVENTORY);
        drawSlotBackgrounds(guiGraphics, offsetX, offsetY, SlotSemantics.PLAYER_HOTBAR);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        guiGraphics.hLine(7, imageWidth - 8, 17, 0xFF808080);
        guiGraphics.hLine(7, imageWidth - 8, 18, 0xFFFFFFFF);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        for (var entry : directionButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != menu.direction;
        }

        minusButtons[0].active = menu.offsetX > -ComponentPlacerSettings.MAX_OFFSET;
        minusButtons[1].active = menu.offsetY > -ComponentPlacerSettings.MAX_OFFSET;
        minusButtons[2].active = menu.offsetZ > -ComponentPlacerSettings.MAX_OFFSET;
        plusButtons[0].active = menu.offsetX < ComponentPlacerSettings.MAX_OFFSET;
        plusButtons[1].active = menu.offsetY < ComponentPlacerSettings.MAX_OFFSET;
        plusButtons[2].active = menu.offsetZ < ComponentPlacerSettings.MAX_OFFSET;
        resetOffsets.active = menu.offsetX != 0 || menu.offsetY != 1 || menu.offsetZ != 0;

        clearSelection.active = menu.hasSelection;
        execute.active = menu.selectionState == ComponentPlacerSelection.Validation.VALID
                && menu.hasCable && menu.hasPart;

        setTextContent("offsetX", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "X", menu.offsetX));
        setTextContent("offsetY", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "Y", menu.offsetY));
        setTextContent("offsetZ", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "Z", menu.offsetZ));
        setTextContent("frequencyText", Platform.p2p().toColoredHexString((short) menu.frequency));
        setTextHidden("aeLinkWarning", menu.aeConnected);
        setTextHidden("aeLinkConnected", !menu.aeConnected);
        execute.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.execute.tooltip")
                .append("\n").append(selectionStatus())));
    }

    private Component selectionStatus() {
        return switch (menu.selectionState) {
            case VALID -> Component.translatable("gui.ae2_batchcraft.component_placer.selection.valid",
                    menu.sizeX, menu.sizeY, menu.sizeZ);
            case INCOMPLETE -> Component.translatable("gui.ae2_batchcraft.component_placer.selection.incomplete");
            case VOLUME_NOT_ALLOWED -> Component.translatable(
                    "gui.ae2_batchcraft.component_placer.selection.volume");
            case TOO_LARGE -> Component.translatable("gui.ae2_batchcraft.component_placer.selection.too_large");
        };
    }

    private List<Component> getCompatibleUpgrades() {
        var tooltip = new ArrayList<Component>();
        tooltip.add(GuiText.CompatibleUpgrades.text());
        tooltip.addAll(Upgrades.getTooltipLinesForMachine(menu.getUpgrades().getUpgradableItem()));
        return tooltip;
    }

    private void addDirectionButton(String widgetId, Direction direction, Component label) {
        var button = addCompactButton(widgetId, label, () -> menu.setDirection(direction));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.direction.tooltip", label)));
        directionButtons.put(direction, button);
    }

    private AE2Button addCompactButton(String widgetId, Component label, Runnable action) {
        var button = new CompactTextButton(label, ignored -> action.run());
        widgets.add(widgetId, button);
        return button;
    }

    private Component directionName(Direction direction) {
        return Component.translatable(
                "gui.ae2_batchcraft.component_placer.direction." + direction.getSerializedName());
    }

    private void drawSlotBackgrounds(GuiGraphics guiGraphics, int offsetX, int offsetY,
                                     appeng.menu.SlotSemantic semantic) {
        for (var slot : menu.getSlots(semantic)) {
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(guiGraphics);
        }
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class CompactTextButton extends AE2Button {
        private static final float TEXT_SCALE = 1.0F;

        private CompactTextButton(Component label, Button.OnPress onPress) {
            super(label, onPress);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            guiGraphics.blitSprite(SPRITES.get(active, isHovered()), getX(), getY(), getWidth(), getHeight());
            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            int color = !active ? 0xFF413F54 : isHovered() ? 0xFF517497 : 0xFFF2F2F2;
            var font = Minecraft.getInstance().font;
            float textWidth = font.width(getMessage()) * TEXT_SCALE;
            float textX = getX() + (getWidth() - textWidth) / 2.0F;
            float textY = Math.round(getY() + (getHeight() - 9 * TEXT_SCALE) / 2.0F);
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(textX, textY, 0);
            pose.scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            guiGraphics.drawString(font, getMessage(), 0, 0, color, false);
            pose.popPose();
        }
    }

    private static final class FrequencyWidget extends AbstractWidget {
        private final IntSupplier frequency;

        private FrequencyWidget(IntSupplier frequency) {
            super(0, 0, 0, 0, Component.translatable("gui.ae2_batchcraft.component_placer.frequency"));
            this.frequency = frequency;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int border = isHovered() ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            guiGraphics.fill(getX() + 1, getY() + 1,
                    getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF202020);

            var colors = Platform.p2p().toColors((short) frequency.getAsInt());
            int cellWidth = (getWidth() - 2) / 2;
            int cellHeight = (getHeight() - 2) / 2;
            for (int i = 0; i < colors.length; i++) {
                int x = getX() + 1 + i % 2 * cellWidth;
                int y = getY() + 1 + i / 2 * cellHeight;
                guiGraphics.fill(x, y, x + cellWidth, y + cellHeight,
                        0xFF000000 | colors[i].mediumVariant);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
