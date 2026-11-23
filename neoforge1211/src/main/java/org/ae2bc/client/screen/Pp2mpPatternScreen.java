package org.ae2bc.client.screen;

import appeng.client.Point;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;
import appeng.parts.encoding.EncodingMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.client.screen.component.DirectionLayout;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Pp2mpPatternScreen extends PatternEncodingTermScreen<Pp2mpEncodingTermMenu> {
    private final DirectionLayout directionLayout;
    private final Blitter backgroundFill;
    private final Blitter borderFill;
    private final Blitter realOutputMarkerBackground;
    private @Nullable ActionButton encodeButton;
    private int encodeButtonBottom;

    public Pp2mpPatternScreen(Pp2mpEncodingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        Direction facing = inventory.player.getDirection();
        this.directionLayout = DirectionLayout.fromPlayerFacing(facing);
        this.backgroundFill = Blitter.texture("guis/pattern.png").src(10, 80, 1, 1);
        this.borderFill = Blitter.texture("guis/pattern.png").src(1, 70, 1, 1);
        this.realOutputMarkerBackground = Blitter.texture("guis/pattern.png").src(132, 129, 35, 24);
        this.setSlotsHidden(SlotSemantics.BLANK_PATTERN, true);
        this.setSlotsHidden(SlotSemantics.ENCODED_PATTERN, true);
        this.setSlotsHidden(SlotSemantics.CRAFTING_GRID, true);
        this.setSlotsHidden(SlotSemantics.CRAFTING_RESULT, true);
        this.setSlotsHidden(SlotSemantics.STONECUTTING_INPUT, true);
        this.setSlotsHidden(SlotSemantics.SMITHING_TABLE_TEMPLATE, true);
        this.setSlotsHidden(SlotSemantics.SMITHING_TABLE_BASE, true);
        this.setSlotsHidden(SlotSemantics.SMITHING_TABLE_ADDITION, true);
    }

    /* 初始化时按样式坐标定位原编写按钮，避免逐帧搜索控件。 */
    @Override
    public void init() {
        super.init();
        this.encodeButton = null;
        Point encodePosition = this.getStyle().getWidget("encodePattern")
                .resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        for (GuiEventListener child : this.children()) {
            if (child instanceof TabButton tabButton
                    && tabButton.getX() >= this.leftPos + 170
                    && tabButton.getX() < this.leftPos + 200) {
                tabButton.visible = false;
            } else if (child instanceof ActionButton actionButton
                    && actionButton.getX() == encodePosition.getX()
                    && actionButton.getY() == encodePosition.getY()) {
                this.encodeButton = actionButton;
                actionButton.setMessage(Component.translatable("gui.ae2_batchcraft.pattern.encode.tooltip"));
                this.encodeButtonBottom = this.topPos + this.imageHeight - actionButton.getY();
            }
        }
    }

    /* 隐藏标记槽后，编写按钮上移 (24 + 6) / 2 = 15 像素以单独居中。 */
    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        boolean markerVisible = this.menu.hasFakeInputs();
        this.menu.getRealOutputMarkerSlot().setActive(markerVisible);
        if (this.encodeButton != null) {
            this.encodeButton.setY(this.topPos + this.imageHeight - this.encodeButtonBottom
                    - (markerVisible ? 0 : 15));
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        this.drawEncodingBackground(graphics, offsetX, offsetY);
    }

    /* 两种界面共用编码区遮盖，标记槽背景保留与编码面板右边框的连接。 */
    protected final void drawEncodingBackground(GuiGraphics graphics, int offsetX, int offsetY) {
        int encodingTop = offsetY + this.imageHeight - 180;
        this.backgroundFill.dest(offsetX + 145, encodingTop + 14, 20, 20).blit(graphics);
        this.backgroundFill.dest(offsetX + 132, encodingTop + 57, 39, 27).blit(graphics);
        this.borderFill.dest(offsetX + 132, encodingTop + 57, 1, 25).blit(graphics);

        Slot markerSlot = this.menu.getRealOutputMarkerSlot();
        if (markerSlot.isActive()) {
            int markerLeft = offsetX + markerSlot.x - 15;
            int markerTop = offsetY + markerSlot.y - 4;
            this.realOutputMarkerBackground.dest(markerLeft, markerTop).blit(graphics);
        }
    }

    /* AE2 单独绘制任务数且不检查按钮可见性，仅在前景绘制期间隐藏该数字。 */
    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        int activeCraftingJobs = this.menu.activeCraftingJobs;
        this.menu.activeCraftingJobs = -1;
        try {
            super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        } finally {
            this.menu.activeCraftingJobs = activeCraftingJobs;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (Screen.hasControlDown() && Minecraft.getInstance().options.keyPickItem.matchesMouse(button)
                && this.menu.getMode() == EncodingMode.PROCESSING) {
            Slot slot = this.getSlotUnderMouse();
            if (slot != null && this.isPrimaryOutputSlot(slot) && slot.hasItem()) {
                this.switchToScreen(new Pp2mpPatternBatchAttrScreen(this));
                return true;
            }
            if (slot != null && this.menu.getSlotSemantic(slot) == SlotSemantics.PROCESSING_INPUTS) {
                int index = this.menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
                this.switchToScreen(new Pp2mpMaterialOutputAttrScreen(this, index, this.directionLayout));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void renderSlot(@NonNull GuiGraphics graphics, @NonNull Slot slot) {
        super.renderSlot(graphics, slot);
        if (this.menu.getMode() != EncodingMode.PROCESSING
                || this.menu.getSlotSemantic(slot) != SlotSemantics.PROCESSING_INPUTS) {
            return;
        }

        int index = this.menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
        MaterialOutputMode mode = this.menu.mode(index);
        Direction direction = this.menu.outputDirection(index);
        if (mode != MaterialOutputMode.NORMAL || direction != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 4, 0xFFFFC83D);
            graphics.fill(slot.x + 12, slot.y, slot.x + 16, slot.y + 1, 0xFFFFC83D);
            graphics.pose().popPose();
        }
    }

    /* 空槽没有物品提示，单独展示槽号、输出方式及配置入口。 */
    @Override
    protected void renderTooltip(@NonNull GuiGraphics graphics, int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();
        if (slot == null || slot.hasItem() || this.menu.getMode() != EncodingMode.PROCESSING
                || this.menu.getSlotSemantic(slot) != SlotSemantics.PROCESSING_INPUTS) {
            super.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        int index = this.menu.getSlots(SlotSemantics.PROCESSING_INPUTS).indexOf(slot);
        this.drawTooltipWithHeader(graphics, mouseX, mouseY, List.of(
                Component.translatable("gui.ae2_batchcraft.pattern.empty_input_slot", index + 1),
                Component.translatable("gui.ae2_batchcraft.material_output_form_value",
                        Component.translatable("gui.ae2_batchcraft.pattern.output_mode." + this.menu.mode(index).name())),
                Component.translatable("gui.ae2_batchcraft.material_output_config").withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Override
    protected @NonNull List<Component> getTooltipFromContainerItem(@NonNull ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot == this.menu.getRealOutputMarkerSlot()) {
            lines = new ArrayList<>(lines);
            lines.addFirst(Component.translatable("gui.ae2_batchcraft.pattern.real_output_marker"));
            lines.add(Component.translatable("gui.ae2_batchcraft.pattern.real_output_marker.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (this.isPrimaryOutputSlot(slot) && slot.hasItem()) {
            lines = new ArrayList<>(lines);
            lines.add(Component.translatable("gui.ae2_batchcraft.pattern_batch_config")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (slot != null
                && this.menu.getMode() == EncodingMode.PROCESSING
                && this.menu.getSlotSemantic(slot) == SlotSemantics.PROCESSING_INPUTS
                && slot.hasItem()) {
            lines = new ArrayList<>(lines);
            lines.add(Component.translatable("gui.ae2_batchcraft.material_output_config")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /* 找到第一个处理输出槽，作为批次数量配置目标。 */
    private boolean isPrimaryOutputSlot(Slot slot) {
        return slot != null
                && this.menu.getMode() == EncodingMode.PROCESSING
                && !this.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).isEmpty()
                && this.menu.getSlots(SlotSemantics.PROCESSING_OUTPUTS).get(0) == slot;
    }
}
