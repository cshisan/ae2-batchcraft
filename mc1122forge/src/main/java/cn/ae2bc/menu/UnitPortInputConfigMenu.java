package cn.ae2bc.menu;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotRestrictedInput;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

/** Legacy material return filter for input-type unit ports. */
public final class UnitPortInputConfigMenu extends AEBaseContainer {
    private final BlockPos pos;
    private final EnumFacing side;
    private final PatternP2PUnitPortPart part;

    public UnitPortInputConfigMenu(EntityPlayer player, BlockPos pos, EnumFacing side) {
        super(player.inventory, findTile(player, pos), findPart(player, pos, side));
        this.pos = pos;
        this.side = side;
        this.part = findPart(player, pos, side);
        if (part != null) {
            IItemHandler markers = part.getInputFilterMarkers();
            for (int i = 0; i < markers.getSlots(); i++) {
                addSlotToContainer(new MarkerSlot(markers, i,
                        8 + i % 9 * 18, 36 + i / 9 * 18));
            }
            addSlotToContainer(new InverterSlot(part.getInputFilterInverter(), 0, 187, 8,
                    player.inventory));
        }
        bindPlayerInventory(player.inventory, 0, 84);
    }

    public PatternP2PUnitPortPart getPart() { return part; }

    private static TileEntity findTile(EntityPlayer player, BlockPos pos) {
        return player.world.getTileEntity(pos);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return findPart(player, pos, side) != null
                && player.getDistanceSq(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64.0;
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        int markerCount = part == null ? 0 : part.getInputFilterMarkers().getSlots();
        if (slotId >= 0 && slotId < markerCount) {
            IItemHandler markers = part.getInputFilterMarkers();
            if (markers instanceof net.minecraftforge.items.IItemHandlerModifiable) {
                ItemStack carried = player.inventory.getItemStack();
                ItemStack marker = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
                if (!marker.isEmpty()) marker.setCount(1);
                ((net.minecraftforge.items.IItemHandlerModifiable) markers).setStackInSlot(slotId, marker);
                return carried;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    public static PatternP2PUnitPortPart findPart(EntityPlayer player, BlockPos pos, EnumFacing side) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof appeng.api.parts.IPartHost)) return null;
        appeng.api.parts.IPart found = ((appeng.api.parts.IPartHost) tile)
                .getPart(appeng.api.util.AEPartLocation.fromFacing(side));
        PatternP2PUnitPortPart result = found instanceof PatternP2PUnitPortPart
                ? (PatternP2PUnitPortPart) found : null;
        return result != null && result.getPortType().returnsTaskOutput() ? result : null;
    }

    private static final class MarkerSlot extends SlotFake {
        private MarkerSlot(IItemHandler inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }
        @Override public boolean isItemValid(ItemStack stack) { return false; }
        @Override public void putStack(ItemStack stack) {
            super.putStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copy().splitStack(1));
        }
    }

    private static final class InverterSlot extends SlotRestrictedInput {
        private InverterSlot(IItemHandler inventory, int slot, int x, int y,
                net.minecraft.entity.player.InventoryPlayer playerInventory) {
            super(PlacableItemType.UPGRADES, inventory, slot, x, y, playerInventory);
            setNotDraggable();
        }
    }
}
