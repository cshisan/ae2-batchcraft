package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PUnitManagerPart;
import cn.ae2bc.registry.ModContent;

import java.util.UUID;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.unit.OutputSlotSharingMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IIntArray;
import net.minecraft.util.IntArray;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public final class PatternP2PUnitManagerMenu extends Container {
    private final PlayerEntity player;
    private final BlockPos pos;
    private final int frequency;
    private final UUID unitId;
    private final PatternP2PUnitSettings settings;
    private final boolean syncMainConfiguration;
    private final cn.ae2bc.logic.EnergyDistributionMode energyDistributionMode;
    private final IntArray data = new IntArray(1);
    private OutputSlotSharingMode outputSlotSharingMode;

    public PatternP2PUnitManagerMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        super(ModContent.UNIT_MANAGER_SETTINGS.get(), id);
        player = inventory.player;
        pos = buffer.readBlockPos();
        frequency = buffer.readInt();
        unitId = buffer.readUUID();
        syncMainConfiguration = buffer.readBoolean();
        energyDistributionMode = cn.ae2bc.logic.EnergyDistributionMode.fromId(buffer.readUnsignedByte());
        settings = new PatternP2PUnitSettings(
                cn.ae2bc.logic.ReturnMode.fromId(buffer.readUnsignedByte()), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(),
                cn.ae2bc.logic.RedstoneOutputMode.fromId(buffer.readUnsignedByte()),
                buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt(),
                cn.ae2bc.core.unit.TransferPortOutputMode.fromId(buffer.readUnsignedByte()),
                cn.ae2bc.core.unit.OutputSlotSharingMode.fromId(buffer.readUnsignedByte()));
        outputSlotSharingMode = settings.getOutputSlotSharingMode();
        data.set(0, outputSlotSharingMode.getId());
        addDataSlots(data);
    }

    public PatternP2PUnitManagerMenu(int id, PlayerInventory inventory, PatternP2PUnitManagerPart part) {
        super(ModContent.UNIT_MANAGER_SETTINGS.get(), id);
        player = inventory.player;
        pos = part.getTile().getBlockPos();
        frequency = part.getFrequencyUnsigned();
        unitId = part.getUnitId();
        syncMainConfiguration = part.isSyncMainConfiguration();
        energyDistributionMode = part.getEnergyDistributionMode();
        settings = part.getSettings();
        outputSlotSharingMode = settings.getOutputSlotSharingMode();
        data.set(0, outputSlotSharingMode.getId());
        addDataSlots(data);
    }

    public BlockPos getPos() { return pos; }
    public int getFrequency() { return frequency; }
    public UUID getUnitId() { return unitId; }
    public PatternP2PUnitSettings getSettings() { return settings; }
    public OutputSlotSharingMode getOutputSlotSharingMode() {
        return OutputSlotSharingMode.fromId(data.get(0));
    }
    public boolean isSyncMainConfiguration() { return syncMainConfiguration; }
    public cn.ae2bc.logic.EnergyDistributionMode getEnergyDistributionMode() { return energyDistributionMode; }

    @Override
    public void broadcastChanges() {
        if (!player.level.isClientSide) {
            PatternP2PUnitManagerPart part = findPart(player, pos);
            if (part != null) data.set(0, part.getOutputSlotSharingMode().getId());
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(PlayerEntity player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && findPart(player, pos) != null;
    }

    public static PatternP2PUnitManagerPart findPart(PlayerEntity player, BlockPos pos) {
        TileEntity tile = player.level.getBlockEntity(pos);
        if (!(tile instanceof IPartHost)) return null;
        IPart part = ((IPartHost) tile).getPart(AEPartLocation.INTERNAL);
        return part instanceof PatternP2PUnitManagerPart ? (PatternP2PUnitManagerPart) part : null;
    }
}
