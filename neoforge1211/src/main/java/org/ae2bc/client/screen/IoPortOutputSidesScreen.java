package org.ae2bc.client.screen;

import appeng.api.config.ActionItems;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.SlotSemantics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.ae2bc.ioport.Ae2bcIoPortBlockEntity;
import org.ae2bc.ioport.Ae2bcIoPortMenu;
import org.ae2bc.ioport.IoPortTransferData;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IoPortOutputSidesScreen extends AESubScreen<Ae2bcIoPortMenu, AEBaseScreen<Ae2bcIoPortMenu>> {
    private final Map<RelativeSide, SideButton> sides = new EnumMap<>(RelativeSide.class);
    private final ActionButton clear;

    public IoPortOutputSidesScreen(AEBaseScreen<Ae2bcIoPortMenu> parent) {
        super(parent, "/screens/ae2_batchcraft/io_port_output_sides.json");
        ScreenHeader.addTo(this.widgets);
        for (RelativeSide side : RelativeSide.values()) {
            SideButton button = new SideButton(ignored ->
                    this.getMenu().toggleOutputSide(side));
            this.sides.put(side, button);
            this.widgets.add(side.name().toLowerCase(Locale.ROOT), button);
        }
        this.clear = new ActionButton(ActionItems.S_CLOSE,
                button -> this.getMenu().clearOutputSides());
        this.clear.setHalfSize(true);
        this.clear.setDisableBackground(true);
        this.widgets.add("clear", this.clear);
        this.clear.setMessage(Component.translatable("gui.ae2_batchcraft.io_port.output_sides.clear"));
        this.widgets.add("return", new TabButton(Icon.BACK, parent.getTitle(), button -> this.returnToParent()));
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    /* 每个按钮展示对应相对面、世界方向及相邻方块，选中状态以服务端回包为准。 */
    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        IoPortTransferData data = this.getMenu().getTransferData();
        Ae2bcIoPortBlockEntity host = this.getMenu().getHost();
        Level level = host.getLevel();
        for (Map.Entry<RelativeSide, SideButton> entry : this.sides.entrySet()) {
            RelativeSide side = entry.getKey();
            Direction direction = host.getOrientation().getSide(side);
            SideButton button = entry.getValue();
            button.setState(data.enabled(side));
            button.active = data.mode().allowsExternal();
            button.adjacentItem = level == null ? ItemStack.EMPTY
                    : IoPortOutputSidesScreen.adjacentItem(
                    level, host.getBlockPos().relative(direction), direction, this.getMenu().getPlayerInventory().player);
            Component label = Component.translatable("gui.ae2_batchcraft.io_port.side."
                    + side.name().toLowerCase(Locale.ROOT));
            Component worldSide = Component.translatable("gui.ae2_batchcraft.direction." + direction.getName());
            Component neighbor = button.adjacentItem.getHoverName();
            button.setTooltipOn(List.of(label, worldSide, neighbor,
                    Component.translatable("gui.ae2_batchcraft.io_port.output_sides.enabled")));
            button.setTooltipOff(List.of(label, worldSide, neighbor,
                    Component.translatable("gui.ae2_batchcraft.io_port.output_sides.not_enabled")));
        }
        this.clear.active = data.mode().allowsExternal() && data.sides() != 0;
    }

    /* 使用相邻方块的拾取图案，保留部件和方块变体对应的物品展示。 */
    private static ItemStack adjacentItem(Level level, BlockPos pos, Direction direction, Player player) {
        if (!level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            IPart part = host.getPart(direction.getOpposite());
            if (part != null) {
                return new ItemStack(part.getPartItem());
            }
        }
        Direction face = direction.getOpposite();
        BlockHitResult hit = new BlockHitResult(pos.getCenter().relative(face, 0.5D), face, pos, false);
        return level.getBlockState(pos).getCloneItemStack(hit, level, pos, player);
    }

    @Override
    public void onClose() {
        this.returnToParent();
    }

    private static final class SideButton extends ToggleButton {
        private ItemStack adjacentItem = ItemStack.EMPTY;

        private SideButton(Listener listener) {
            super(Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS, Icon.TOOLBAR_BUTTON_BACKGROUND, listener);
        }

        /* 与水晶装配器一致：背景表示选中状态，中心仅展示相邻物品。 */
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            int yOffset = this.isHovered() ? 1 : 0;
            Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.getIcon();
            background.getBlitter().dest(
                    this.getX() - 1, this.getY() + yOffset, 18, 20
            ).zOffset(2).blit(graphics);
            if (!this.adjacentItem.isEmpty()) {
                graphics.renderItem(this.adjacentItem, this.getX(), this.getY() + 1 + yOffset, 0, 3);
            }
        }
    }
}
