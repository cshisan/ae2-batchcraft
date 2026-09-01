package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PTunnelEnergyPart;
import cn.ae2bc.registry.ModContent;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import cn.ae2bc.logic.EnergyDistributionMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public final class PatternP2PTunnelEnergyMenu extends Container {
    private final BlockPos pos;
    private final Direction side;
    private final boolean pullEnabled;
    private final EnergyDistributionMode mode;

    public PatternP2PTunnelEnergyMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        super(ModContent.PATTERN_P2P_ENERGY_SETTINGS.get(), id);
        pos = buffer.readBlockPos();
        side = Direction.values()[buffer.readUnsignedByte() % Direction.values().length];
        pullEnabled = buffer.readBoolean();
        mode = EnergyDistributionMode.fromId(buffer.readUnsignedByte());
    }

    public PatternP2PTunnelEnergyMenu(int id, PlayerInventory inventory, PatternP2PTunnelEnergyPart part) {
        super(ModContent.PATTERN_P2P_ENERGY_SETTINGS.get(), id);
        pos = part.getTile().getBlockPos();
        side = part.getSide().getFacing();
        pullEnabled = part.isPullEnabled();
        mode = part.getDistributionMode();
    }

    public BlockPos getPos() { return pos; }
    public Direction getSide() { return side; }
    public boolean isPullEnabled() { return pullEnabled; }
    public EnergyDistributionMode getMode() { return mode; }

    @Override public boolean stillValid(PlayerEntity player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && findPart(player, pos, side) != null;
    }

    public static PatternP2PTunnelEnergyPart findPart(PlayerEntity player, BlockPos pos, Direction side) {
        TileEntity tile = player.level.getBlockEntity(pos);
        if (!(tile instanceof IPartHost)) return null;
        IPart part = ((IPartHost) tile).getPart(side);
        return part instanceof PatternP2PTunnelEnergyPart ? (PatternP2PTunnelEnergyPart) part : null;
    }
}
