package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotRestrictedInput;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;

/** Legacy container for output-port filters and priority. */
public final class UnitPortOutputConfigMenu extends AEBaseContainer {
    private final EntityPlayer player;
    private final BlockPos pos;
    private final EnumFacing side;
    private final PatternP2PUnitPortPart part;
    public int priority;
    public boolean singleSlot;
    public boolean singleSlotEditable;
    private boolean stateSynchronized;

    public UnitPortOutputConfigMenu(EntityPlayer player, BlockPos pos, EnumFacing side) {
        super(player.inventory, findTile(player, pos), findPart(player, pos, side));
        this.player = player;
        this.pos = pos;
        this.side = side;
        this.part = findPart(player, pos, side);
        this.priority = part == null ? 0 : part.getPriority();
        this.singleSlot = part != null && part.getEffectiveSingleSlot();
        this.singleSlotEditable = part == null || part.isSingleSlotEditable();
        if (part != null) {
            IItemHandler markers = part.getOutputFilterMarkers();
            for (int i = 0; i < markers.getSlots(); i++) {
                addSlotToContainer(new MarkerSlot(markers, i, 8 + i % 9 * 18, 102 + i / 9 * 18));
            }
            addSlotToContainer(new InverterSlot(part.getOutputFilterInverter(), 0, 187, 8,
                    player.inventory));
        }
        bindPlayerInventory(player.inventory, 0, 150);
    }

    @Override
    public void detectAndSendChanges() {
        refreshStateAndSync(false);
        super.detectAndSendChanges();
    }

    /** Refreshes the client-facing values from the authoritative port state. */
    public void refreshStateAndSync() {
        refreshStateAndSync(false);
    }

    /** Forces a reply after client actions so optimistic client state cannot remain visible. */
    public void refreshStateAndSync(boolean force) {
        if (player.world.isRemote || part == null) return;
        PatternP2PUnitManagerPart manager = part.findManager();
        if (manager != null && manager.isSyncMainConfiguration()) {
            manager.synchronizeFromInput();
        }
        int nextPriority = part.getPriority();
        boolean nextSingleSlot = part.getEffectiveSingleSlot();
        boolean nextEditable = part.isSingleSlotEditable();
        boolean changed = priority != nextPriority || singleSlot != nextSingleSlot
                || singleSlotEditable != nextEditable;
        priority = nextPriority;
        singleSlot = nextSingleSlot;
        singleSlotEditable = nextEditable;
        if (!force && stateSynchronized && !changed) return;
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            cn.ae2bc.network.ModNetwork.sendUnitPortState(
                    (net.minecraft.entity.player.EntityPlayerMP) player, this);
            stateSynchronized = true;
        }
    }

    public BlockPos getPos() { return pos; }
    public EnumFacing getSide() { return side; }
    public PatternP2PUnitPortPart getPart() { return part; }

    private static TileEntity findTile(EntityPlayer player, BlockPos pos) {
        return player.world.getTileEntity(pos);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return part != null && part.getPortType().acceptsTaskInput()
                && player.getDistanceSq(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64.0;
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        int markerCount = part == null ? 0 : part.getOutputFilterMarkers().getSlots();
        if (slotId >= 0 && slotId < markerCount) {
            IItemHandler markers = part.getOutputFilterMarkers();
            if (markers instanceof net.minecraftforge.items.IItemHandlerModifiable) {
                ItemStack carried = player.inventory.getItemStack();
                if (carried.isEmpty()) {
                    ((net.minecraftforge.items.IItemHandlerModifiable) markers).setStackInSlot(slotId, ItemStack.EMPTY);
                } else {
                    ItemStack marked = carried.copy();
                    marked.setCount(1);
                    ((net.minecraftforge.items.IItemHandlerModifiable) markers).setStackInSlot(slotId, marked);
                }
                return carried;
            }
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    public static PatternP2PUnitPortPart findPart(EntityPlayer player, BlockPos pos, EnumFacing side) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof appeng.api.parts.IPartHost)) return null;
        appeng.api.parts.IPart part = ((appeng.api.parts.IPartHost) tile)
                .getPart(appeng.api.util.AEPartLocation.fromFacing(side));
        PatternP2PUnitPortPart result = part instanceof PatternP2PUnitPortPart ? (PatternP2PUnitPortPart) part : null;
        return result != null && result.getPortType().acceptsTaskInput() ? result : null;
    }

    private static final class MarkerSlot extends SlotFake {
        private MarkerSlot(IItemHandler inventory, int slot, int x, int y) { super(inventory, slot, x, y); }
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
