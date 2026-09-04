package cn.ae2bc.menu;

import appeng.container.slot.AppEngSlot;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.registry.ModContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Collections;
import java.util.List;

/** Material return filter for input-type unit ports. */
public final class UnitPortInputConfigMenu extends Container {
    private final BlockPos pos;
    private final Direction side;
    private final PatternP2PUnitPortPart part;
    private InverterSlot inverterSlot;

    public UnitPortInputConfigMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        this(id, inventory, buffer.readBlockPos(),
                Direction.values()[buffer.readUnsignedByte() % Direction.values().length], null);
    }

    public UnitPortInputConfigMenu(int id, PlayerInventory inventory, PatternP2PUnitPortPart part) {
        this(id, inventory, part.getTile().getBlockPos(), part.getSide().getFacing(), part);
    }

    private UnitPortInputConfigMenu(int id, PlayerInventory inventory, BlockPos pos, Direction side,
            PatternP2PUnitPortPart part) {
        super(ModContent.UNIT_PORT_INPUT_CONFIG.get(), id);
        this.pos = pos;
        this.side = side;
        this.part = part == null ? findPart(inventory.player, pos, side) : part;
        if (this.part != null) {
            IItemHandler markers = this.part.getInputFilterMarkers();
            for (int i = 0; i < markers.getSlots(); i++) {
                addSlot(new MarkerSlot(markers, i, 8 + (i % 9) * 18, 36 + (i / 9) * 18));
            }
            inverterSlot = new InverterSlot(this.part.getInputFilterInverter(), 0);
            addSlot(inverterSlot);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    public PatternP2PUnitPortPart getPart() { return part; }

    public List<Slot> getUpgradeSlots() {
        return inverterSlot == null ? Collections.<Slot>emptyList()
                : Collections.<Slot>singletonList(inverterSlot);
    }

    @Override
    public boolean stillValid(PlayerEntity player) {
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && findPart(player, pos, side) != null;
    }

    public static PatternP2PUnitPortPart findPart(PlayerEntity player, BlockPos pos, Direction side) {
        if (!(player.level.getBlockEntity(pos) instanceof appeng.api.parts.IPartHost)) return null;
        appeng.api.parts.IPart found = ((appeng.api.parts.IPartHost) player.level.getBlockEntity(pos))
                .getPart(appeng.api.util.AEPartLocation.fromFacing(side));
        PatternP2PUnitPortPart result = found instanceof PatternP2PUnitPortPart
                ? (PatternP2PUnitPortPart) found : null;
        return result != null && result.getPortType().returnsTaskOutput() ? result : null;
    }

    @Override public ItemStack quickMoveStack(PlayerEntity player, int index) { return ItemStack.EMPTY; }

    @Override
    public ItemStack clicked(int slotId, int dragType, ClickType clickType, PlayerEntity player) {
        int markerCount = part == null ? 0 : part.getInputFilterMarkers().getSlots();
        if (slotId >= 0 && slotId < markerCount) {
            IItemHandler markers = part.getInputFilterMarkers();
            if (markers instanceof IItemHandlerModifiable) {
                ItemStack carried = player.inventory.getCarried();
                ItemStack marker = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
                if (!marker.isEmpty()) marker.setCount(1);
                ((IItemHandlerModifiable) markers).setStackInSlot(slotId, marker);
                return carried;
            }
        }
        return super.clicked(slotId, dragType, clickType, player);
    }

    private static final class MarkerSlot extends SlotItemHandler {
        private MarkerSlot(IItemHandler handler, int slot, int x, int y) {
            super(handler, slot, x, y);
        }
    }

    private static final class InverterSlot extends AppEngSlot {
        private InverterSlot(IItemHandler handler, int slot) {
            super(handler, slot);
        }
    }
}
