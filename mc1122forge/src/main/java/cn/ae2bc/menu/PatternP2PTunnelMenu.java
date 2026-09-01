package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PTunnelPart;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/** Server/client container used only to validate the distance and endpoint identity. */
public final class PatternP2PTunnelMenu extends Container {
    private final EntityPlayer player;
    private final BlockPos pos;
    private final EnumFacing side;
    private final PatternP2PTunnelPart part;

    public PatternP2PTunnelMenu(EntityPlayer player, BlockPos pos, EnumFacing side) {
        this.player = player;
        this.pos = pos;
        this.side = side;
        this.part = findPart(player, pos, side);
    }

    public PatternP2PTunnelPart getPart() { return part; }
    public BlockPos getPos() { return pos; }
    public EnumFacing getSide() { return side; }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return part != null && player.getDistanceSq(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public static PatternP2PTunnelPart findPart(EntityPlayer player, BlockPos pos, EnumFacing side) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof IPartHost)) return null;
        IPart candidate = ((IPartHost) tile).getPart(side);
        return candidate instanceof PatternP2PTunnelPart ? (PatternP2PTunnelPart) candidate : null;
    }

    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}
