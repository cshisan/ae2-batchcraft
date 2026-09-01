package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PUnitPortPart;
import cn.ae2bc.registry.ModContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.IntArray;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import appeng.container.slot.AppEngSlot;

import java.util.Collections;
import java.util.List;

public final class UnitPortOutputConfigMenu extends Container {
    private final PlayerEntity player;
    private final BlockPos pos;
    private final PatternP2PUnitPortPart part;
    private final Direction side;
    private InverterSlot inverterSlot;
    public int priority;
    public boolean singleSlot;
    public boolean singleSlotEditable;
    private final IntArray data = new IntArray(2);
    public UnitPortOutputConfigMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        this(id, inventory, buffer.readBlockPos(),
                Direction.values()[buffer.readUnsignedByte() % Direction.values().length], null,
                Boolean.valueOf(buffer.readBoolean()), Boolean.valueOf(buffer.readBoolean()));
    }
    public UnitPortOutputConfigMenu(int id, PlayerInventory inventory, PatternP2PUnitPortPart part) {
        this(id, inventory, part.getTile().getBlockPos(), part.getSide().getFacing(), part, null, null);
    }
    private UnitPortOutputConfigMenu(int id, PlayerInventory inventory, BlockPos pos, Direction side,
            PatternP2PUnitPortPart part, Boolean initialSingleSlot, Boolean initialSingleSlotEditable) {
        super(ModContent.UNIT_PORT_OUTPUT_CONFIG.get(), id);
        this.player = inventory.player;
        this.pos = pos;
        this.side = side;
        this.part = part == null ? findPart(inventory.player, pos, side) : part;
        this.priority = this.part == null ? 0 : this.part.getPriority();
        this.singleSlot = initialSingleSlot != null ? initialSingleSlot.booleanValue()
                : this.part != null && this.part.getEffectiveSingleSlot();
        this.singleSlotEditable = initialSingleSlotEditable != null
                ? initialSingleSlotEditable.booleanValue()
                : this.part == null || this.part.isSingleSlotEditable();
        data.set(0, this.singleSlot ? 1 : 0);
        data.set(1, this.singleSlotEditable ? 1 : 0);
        addDataSlots(data);
        if (this.part != null) {
            IItemHandler markers = this.part.getOutputFilterMarkers();
            for (int i = 0; i < markers.getSlots(); i++) addSlot(new MarkerSlot(markers, i, 8 + (i % 9) * 18, 110 + (i / 9) * 18));
            inverterSlot = new InverterSlot(this.part.getOutputFilterInverter(), 0);
            addSlot(inverterSlot);
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 216));
    }
    public BlockPos getPos() { return pos; }
    public Direction getSide() { return side; }
    public PatternP2PUnitPortPart getPart() { return part; }
    public boolean getSyncedSingleSlot() { return data.get(0) != 0; }
    public boolean getSyncedSingleSlotEditable() { return data.get(1) != 0; }
    public List<Slot> getUpgradeSlots() {
        return inverterSlot == null ? Collections.<Slot>emptyList() : Collections.<Slot>singletonList(inverterSlot);
    }
    @Override public boolean stillValid(PlayerEntity player) { return player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 64 && isOutputPart(findPart(player,pos,side)); }
    private static boolean isOutputPart(PatternP2PUnitPortPart part) { return part != null && part.getPortType().acceptsTaskInput(); }
    public static PatternP2PUnitPortPart findPart(PlayerEntity player, BlockPos pos, Direction side) {
        if (!(player.level.getBlockEntity(pos) instanceof appeng.api.parts.IPartHost)) return null;
        appeng.api.parts.IPart p = ((appeng.api.parts.IPartHost) player.level.getBlockEntity(pos))
                .getPart(appeng.api.util.AEPartLocation.fromFacing(side));
        PatternP2PUnitPortPart result = p instanceof PatternP2PUnitPortPart ? (PatternP2PUnitPortPart) p : null;
        return isOutputPart(result) ? result : null;
    }
    @Override
    public void broadcastChanges() {
        if (!player.level.isClientSide) {
            PatternP2PUnitPortPart current = findPart(player, pos, side);
            if (current != null) {
                data.set(0, current.getEffectiveSingleSlot() ? 1 : 0);
                data.set(1, current.isSingleSlotEditable() ? 1 : 0);
                priority = current.getPriority();
            }
        }
        super.broadcastChanges();
    }
    @Override public net.minecraft.item.ItemStack quickMoveStack(PlayerEntity player, int index) { return net.minecraft.item.ItemStack.EMPTY; }

    @Override
    public net.minecraft.item.ItemStack clicked(int slotId, int dragType, ClickType clickType, PlayerEntity player) {
        int markerCount = part == null ? 0 : part.getOutputFilterMarkers().getSlots();
        if (slotId >= 0 && slotId < markerCount) {
            IItemHandler markers = part.getOutputFilterMarkers();
            if (markers instanceof net.minecraftforge.items.IItemHandlerModifiable) {
                net.minecraft.item.ItemStack carried = player.inventory.getCarried();
                if (carried.isEmpty()) {
                    ((net.minecraftforge.items.IItemHandlerModifiable) markers).setStackInSlot(slotId, net.minecraft.item.ItemStack.EMPTY);
                } else {
                    net.minecraft.item.ItemStack marked = carried.copy();
                    marked.setCount(1);
                    ((net.minecraftforge.items.IItemHandlerModifiable) markers).setStackInSlot(slotId, marked);
                }
                return carried;
            }
        }
        return super.clicked(slotId, dragType, clickType, player);
    }

    private static class MarkerSlot extends SlotItemHandler {
        MarkerSlot(IItemHandler handler, int slot, int x, int y) { super(handler, slot, x, y); }
    }
    private static class InverterSlot extends AppEngSlot {
        InverterSlot(IItemHandler handler, int slot) { super(handler, slot); }
    }
}
