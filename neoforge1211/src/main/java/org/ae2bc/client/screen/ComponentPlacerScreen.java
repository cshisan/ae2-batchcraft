package org.ae2bc.client.screen;

import appeng.api.upgrades.Upgrades;
import appeng.api.util.AEColor;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.util.Platform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.core.placer.DataModel;
import org.ae2bc.client.screen.component.ScrollingAE2Button;
import org.ae2bc.core.placer.SelectionStatus;
import org.ae2bc.placer.ComponentPlacerMenu;
import org.ae2bc.placer.ComponentPlacerPose;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class ComponentPlacerScreen extends AEBaseScreen<ComponentPlacerMenu> {
    private static final Blitter HEADER_DARK = Blitter.texture("guis/background.png").src(4, 253, 162, 1);
    private static final Blitter HEADER_LIGHT = Blitter.texture("guis/background.png").src(4, 1, 162, 1);
    private final Map<Direction, AE2Button> directionButtons = new EnumMap<>(Direction.class);
    private final AE2Button[] minusButtons = new AE2Button[3];
    private final AE2Button[] plusButtons = new AE2Button[3];
    private final AE2Button resetOffsets;
    private final AE2Button clearSelection;
    private final AE2Button execute;
    private final StringWidget aeLink;

    public ComponentPlacerScreen(ComponentPlacerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        FrequencyWidget frequencyWidget = new FrequencyWidget(
                Component.translatable("gui.ae2_batchcraft.component_placer.frequency"),
                () -> menu.getModel().frequency(), menu::clearFrequency, menu::loadFrequency
        );
        frequencyWidget.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.frequency.tooltip"
        )));
        this.widgets.add("frequency", frequencyWidget);
        FrequencyWidget unitIdWidget = new FrequencyWidget(
                Component.translatable("gui.ae2_batchcraft.component_placer.unit_id"),
                () -> menu.getModel().unitId(), menu::clearUnitId, menu::loadUnitId
        );
        unitIdWidget.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.unit_id.tooltip"
        )));
        this.widgets.add("unitId", unitIdWidget);
        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), this::getCompatibleUpgrades));
        this.aeLink = new StringWidget(Component.empty(), this.font).alignRight();
        this.widgets.add("aeLink", this.aeLink);

        Direction front = playerInventory.player.getDirection();
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();
        this.addDirectionButton("directionFront", front, this.directionName(front));
        this.addDirectionButton("directionLeft", left, Component.translatable(
                "gui.ae2_batchcraft.component_placer.relative.left", this.directionName(left)));
        this.addDirectionButton("directionUp", Direction.UP, this.directionName(Direction.UP));
        this.addDirectionButton("directionRight", right, Component.translatable(
                "gui.ae2_batchcraft.component_placer.relative.right", this.directionName(right)));
        this.addDirectionButton("directionDown", Direction.DOWN, this.directionName(Direction.DOWN));
        this.addDirectionButton("directionBack", front.getOpposite(), this.directionName(front.getOpposite()));

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < axes.length; i++) {
            int axis = i;
            this.minusButtons[i] = this.addCompactButton(
                    "offset" + axes[i] + "Minus",
                    Component.literal("-"),
                    () -> menu.adjustOffset(axis, -1)
            );
            this.minusButtons[i].setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.component_placer.offset.decrease.tooltip",
                    axes[i]
            )));
            this.plusButtons[i] = this.addCompactButton(
                    "offset" + axes[i] + "Plus",
                    Component.literal("+"),
                    () -> menu.adjustOffset(axis, 1)
            );
            this.plusButtons[i].setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.component_placer.offset.increase.tooltip",
                    axes[i]
            )));
        }

        this.resetOffsets = this.addCompactButton("resetOffsets",
                Component.translatable("gui.ae2_batchcraft.component_placer.reset_offsets"), menu::resetOffsets);
        this.resetOffsets.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.reset_offsets.tooltip")));
        this.clearSelection = this.addCompactButton("clearSelection",
                Component.translatable("gui.ae2_batchcraft.component_placer.clear_selection"), menu::clearSelection);
        this.clearSelection.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.clear_selection.tooltip")));
        this.execute = this.addCompactButton("execute",
                Component.translatable("gui.ae2_batchcraft.component_placer.execute"), menu::execute);
        this.execute.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.execute.tooltip")));
    }

    /* 标签按独立列限宽，资源包字体过宽时通过悬浮提示查看完整说明。 */
    @Override
    protected void init() {
        super.init();
        this.setMarkerLabel("cable_label", "gui.ae2_batchcraft.component_placer.cable");
        this.setMarkerLabel("part_label", "gui.ae2_batchcraft.component_placer.part");
        this.setMarkerLabel("frequencyText", "gui.ae2_batchcraft.component_placer.frequency");
        this.setMarkerLabel("unitIdText", "gui.ae2_batchcraft.component_placer.unit_id");
    }

    private void setMarkerLabel(String textId, String translationKey) {
        Component label = Component.translatable(translationKey);
        if (this.font.width(label) > 36) {
            String ellipsis = "...";
            String prefix = this.font.plainSubstrByWidth(label.getString(), 36 - this.font.width(ellipsis));
            label = Component.literal(prefix + ellipsis);
        }
        this.setTextContent(textId, label);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        ComponentPlacerScreen.HEADER_DARK.dest(offsetX + 7, offsetY + 17, this.imageWidth - 14, 1).blit(graphics);
        ComponentPlacerScreen.HEADER_LIGHT.dest(offsetX + 7, offsetY + 18, this.imageWidth - 14, 1).blit(graphics);
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(ComponentPlacerMenu.CABLE_MARKER_SLOT));
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(ComponentPlacerMenu.PART_MARKER_SLOT));
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(SlotSemantics.STORAGE));
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        SlotGroupRenderer.draw(graphics, offsetX, offsetY, this.menu.getSlots(SlotSemantics.PLAYER_HOTBAR));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        DataModel data = this.menu.getModel();
        Direction currentDirection = this.menu.getDirection();
        for (Map.Entry<Direction, AE2Button> entry : this.directionButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != currentDirection;
        }
        this.minusButtons[0].active = data.pos1().x() > -ComponentPlacerPose.MAX_OFFSET;
        this.minusButtons[1].active = data.pos1().y() > -ComponentPlacerPose.MAX_OFFSET;
        this.minusButtons[2].active = data.pos1().z() > -ComponentPlacerPose.MAX_OFFSET;
        this.plusButtons[0].active = data.pos1().x() < ComponentPlacerPose.MAX_OFFSET;
        this.plusButtons[1].active = data.pos1().y() < ComponentPlacerPose.MAX_OFFSET;
        this.plusButtons[2].active = data.pos1().z() < ComponentPlacerPose.MAX_OFFSET;
        this.resetOffsets.active = !data.pos1().equals(ComponentPlacerPose.DEFAULT.pos());
        this.clearSelection.active = data.hasSelection();
        this.execute.active = data.selection() == SelectionStatus.VALID && data.hasCable() && data.hasPart();

        this.setTextContent("offsetX", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "X", data.pos1().x()));
        this.setTextContent("offsetY", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "Y", data.pos1().y()));
        this.setTextContent("offsetZ", Component.translatable(
                "gui.ae2_batchcraft.component_placer.offset_value", "Z", data.pos1().z()));
        this.setTextContent("modeText", Component.translatable(data.frequencyMode()
                ? "gui.ae2_batchcraft.component_placer.mode.frequency"
                : "gui.ae2_batchcraft.component_placer.mode.placement"));
        String statusKey = data.aeConnected() ? "linked"
                : this.menu.isPaired() ? "disconnected" : "not_linked";
        this.aeLink.setMessage(Component.translatable("gui.ae2_batchcraft.component_placer." + statusKey));
        this.aeLink.setColor(data.aeConnected() ? 0x55FF55 : 0xFF5555);
        MutableComponent linkTooltip = Component.translatable(
                "gui.ae2_batchcraft.component_placer." + statusKey + ".tooltip");
        Component linkError = this.menu.getLinkError();
        if (!data.aeConnected() && linkError != null) {
            linkTooltip.append("\n").append(linkError);
        }
        this.aeLink.setTooltip(Tooltip.create(linkTooltip));
        this.execute.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.execute.tooltip").append("\n").append(this.selectionStatus())));
    }

    private Component selectionStatus() {
        DataModel data = this.menu.getModel();
        SelectionStatus selectionState = data.selection();
        return switch (selectionState) {
            case SelectionStatus.VALID -> Component.translatable(
                    "gui.ae2_batchcraft.component_placer.selection.valid",
                    data.pos2().x(), data.pos2().y(), data.pos2().z()
            );
            case SelectionStatus.INCOMPLETE ->
                    Component.translatable("gui.ae2_batchcraft.component_placer.selection.incomplete");
            case SelectionStatus.SELECTION_NOT_ALLOWED ->
                    Component.translatable("gui.ae2_batchcraft.component_placer.selection.volume");
            case SelectionStatus.TOO_LARGE ->
                    Component.translatable("gui.ae2_batchcraft.component_placer.selection.too_large");
        };
    }

    private List<Component> getCompatibleUpgrades() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(GuiText.CompatibleUpgrades.text());
        tooltip.addAll(Upgrades.getTooltipLinesForMachine(this.menu.getUpgrades().getUpgradableItem()));
        return tooltip;
    }

    private void addDirectionButton(String widgetId, Direction direction, Component label) {
        AE2Button button = this.addCompactButton(widgetId, label, () -> this.menu.setDirection(direction));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.component_placer.direction.tooltip", label)));
        this.directionButtons.put(direction, button);
    }

    private AE2Button addCompactButton(String widgetId, Component label, Runnable action) {
        AE2Button button = new CompactTextButton(label, ignored -> action.run());
        this.widgets.add(widgetId, button);
        return button;
    }

    private Component directionName(Direction direction) {
        return Component.translatable("gui.ae2_batchcraft.component_placer.direction." + direction.getSerializedName());
    }

    private static final class CompactTextButton extends ScrollingAE2Button {

        private CompactTextButton(Component label, Button.OnPress onPress) {
            super(label, onPress);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            graphics.blitSprite(SPRITES.get(this.active, this.isHovered()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            int color = !this.active ? 0xFF413F54 : this.isHovered() ? 0xFF517497 : 0xFFF2F2F2;
            Font font = Minecraft.getInstance().font;
            int textY = this.getY() + Math.round((this.getHeight() - font.lineHeight) / 2.0F);
            this.renderLabel(graphics, font, 2, color, textY);
        }
    }

    private final class FrequencyWidget extends AbstractWidget {
        private final IntSupplier frequency;
        private final Runnable clear;
        private final Runnable load;

        private FrequencyWidget(Component label, IntSupplier frequency, Runnable clear, Runnable load) {
            super(0, 0, 0, 0, label);
            this.frequency = frequency;
            this.clear = clear;
            this.load = load;
        }

        @Override
        protected boolean isValidClickButton(int button) {
            return button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        }

        /* 右键加载、左键清除，并识别 AE2 转发成左键的右键事件。 */
        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || ComponentPlacerScreen.this.isHandlingRightClick()) {
                this.load.run();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.clear.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int border = this.isHovered() ? 0xFFFFFFFF : 0xFF8B8B8B;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), border);
            graphics.fill(this.getX() + 1, this.getY() + 1,
                    this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, 0xFF202020);
            AEColor[] colors = Platform.p2p().toColors((short) this.frequency.getAsInt());
            int cellWidth = (this.getWidth() - 2) / 2;
            int cellHeight = (this.getHeight() - 2) / 2;
            for (int i = 0; i < colors.length; i++) {
                int x = this.getX() + 1 + i % 2 * cellWidth;
                int y = this.getY() + 1 + i / 2 * cellHeight;
                graphics.fill(x, y, x + cellWidth, y + cellHeight, 0xFF000000 | colors[i].mediumVariant);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.getMessage());
        }
    }
}
