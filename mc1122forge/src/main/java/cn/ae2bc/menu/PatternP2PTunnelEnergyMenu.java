package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PTunnelEnergyPart;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class PatternP2PTunnelEnergyMenu extends Container {
    private final BlockPos pos;
    private final EnumFacing side;
    private final PatternP2PTunnelEnergyPart part;

    public PatternP2PTunnelEnergyMenu(EntityPlayer player, BlockPos pos, EnumFacing side) {
        this.pos = pos;
        this.side = side;
        this.part = findPart(player, pos, side);
    }

    public BlockPos getPos() { return pos; }
    public EnumFacing getSide() { return side; }
    public PatternP2PTunnelEnergyPart getPart() { return part; }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return part != null && player.getDistanceSq(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public static PatternP2PTunnelEnergyPart findPart(EntityPlayer player, BlockPos pos, EnumFacing side) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof IPartHost)) return null;
        IPart candidate = ((IPartHost) tile).getPart(side);
        return candidate instanceof PatternP2PTunnelEnergyPart
                ? (PatternP2PTunnelEnergyPart) candidate : null;
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }
}
