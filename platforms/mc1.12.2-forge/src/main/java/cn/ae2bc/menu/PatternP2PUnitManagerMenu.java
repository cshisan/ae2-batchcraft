package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PUnitManagerPart;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public final class PatternP2PUnitManagerMenu extends Container {
    private final BlockPos pos;
    private final PatternP2PUnitManagerPart part;

    public PatternP2PUnitManagerMenu(EntityPlayer player, BlockPos pos) {
        this.pos = pos;
        this.part = findPart(player, pos);
    }
    public BlockPos getPos() { return pos; }
    public PatternP2PUnitManagerPart getPart() { return part; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return part != null && player.getDistanceSq(pos.getX() + 0.5,
                pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    public static PatternP2PUnitManagerPart findPart(EntityPlayer player, BlockPos pos) {
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof IPartHost)) return null;
        IPart candidate = ((IPartHost) tile).getPart(AEPartLocation.INTERNAL);
        return candidate instanceof PatternP2PUnitManagerPart
                ? (PatternP2PUnitManagerPart) candidate : null;
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
