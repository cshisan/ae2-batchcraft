package cn.ae2bc.menu;

import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.registry.ModContent;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import cn.ae2bc.core.unit.PatternP2PUnitSettings;
import cn.ae2bc.core.dispatch.TaskAllocationMode;

public final class PatternP2PTunnelMenu extends Container {
    private final PlayerEntity player;
    private final BlockPos pos;
    private final Direction side;
    private final PatternP2PTunnelPart part;
    private boolean extractionEnabled;
    private final boolean output;
    private final PatternP2PUnitSettings settings;
    private final boolean syncInputSettings;
    private final TaskAllocationMode taskAllocationMode;

    public PatternP2PTunnelMenu(int id, PlayerInventory inventory, PacketBuffer buffer) {
        super(ModContent.PATTERN_P2P_SETTINGS.get(), id);
        this.player = inventory.player;
        this.pos = buffer.readBlockPos();
        this.side = Direction.values()[buffer.readUnsignedByte() % Direction.values().length];
        this.output = buffer.readBoolean();
        this.extractionEnabled = buffer.readBoolean();
        this.settings = readSettings(buffer);
        this.syncInputSettings = buffer.readBoolean();
        this.taskAllocationMode = TaskAllocationMode.fromId(buffer.readUnsignedByte());
        this.part = findPart(player, pos, side);
    }

    public PatternP2PTunnelMenu(int id, PlayerInventory inventory, PatternP2PTunnelPart part) {
        super(ModContent.PATTERN_P2P_SETTINGS.get(), id);
        this.player = inventory.player;
        this.part = part;
        this.pos = part.getTile().getBlockPos();
        this.side = part.getSide().getFacing();
        this.output = part.isOutput();
        this.extractionEnabled = part.isExtractionEnabled();
        this.settings = part.getUnitSettings();
        this.syncInputSettings = part.isSyncInputSettings();
        this.taskAllocationMode = part.getTaskAllocationMode();
    }

    public boolean isExtractionEnabled() { return extractionEnabled; }
    public int getExtractionInterval() { return settings.getExtractionInterval(); }
    public int getExtractionAmount() { return settings.getExtractionAmount(); }
    public boolean isOutput() { return output; }
    /** Only the real Pattern P2P input part owns global unit-port slot sharing. */
    public boolean isInputConfiguration() {
        return !output && part != null && !part.isOutput();
    }
    public PatternP2PUnitSettings getSettings() { return settings; }
    public boolean isSyncInputSettings() { return syncInputSettings; }
    public TaskAllocationMode getTaskAllocationMode() { return taskAllocationMode; }
    public BlockPos getPos() { return pos; }
    public Direction getSide() { return side; }

    @Override
    public boolean stillValid(PlayerEntity player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && findPart(player, pos, side) != null;
    }

    public static PatternP2PTunnelPart findPart(PlayerEntity player, BlockPos pos, Direction side) {
        TileEntity tile = player.level.getBlockEntity(pos);
        if (!(tile instanceof IPartHost)) {
            return null;
        }
        IPart candidate = ((IPartHost) tile).getPart(side);
        return candidate instanceof PatternP2PTunnelPart ? (PatternP2PTunnelPart) candidate : null;
    }

    private static PatternP2PUnitSettings readSettings(PacketBuffer buffer) {
        return new PatternP2PUnitSettings(
                cn.ae2bc.logic.ReturnMode.fromId(buffer.readUnsignedByte()), buffer.readBoolean(),
                buffer.readInt(), buffer.readInt(),
                cn.ae2bc.logic.RedstoneOutputMode.fromId(buffer.readUnsignedByte()),
                buffer.readUnsignedByte(), buffer.readInt(), buffer.readInt(),
                cn.ae2bc.core.unit.TransferPortOutputMode.fromId(buffer.readUnsignedByte()),
                cn.ae2bc.core.unit.OutputSlotSharingMode.fromId(buffer.readUnsignedByte()),
                cn.ae2bc.logic.EnergyDistributionMode.fromId(buffer.readUnsignedByte()));
    }
}
