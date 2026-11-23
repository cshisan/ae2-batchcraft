package org.ae2bc.placer;

import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.SettingsFrom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;

final class Placement {

    static boolean placeAt(
            ServerLevel level, ServerPlayer player, BlockPos pos,
            IPartHost existingHost, IPartItem<?> cableItem, ItemStack partStack,
            Direction direction, short frequency, short unitId
    ) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        boolean reuseCable = Placement.hasMarkedCable(existingHost, cableItem);
        IPartHost host = reuseCable ? existingHost : PartHelper.getOrPlacePartHost(level, pos, false, player);
        if (host == null) {
            return false;
        }

        IPart cable = null;
        if (!reuseCable) {
            cable = host.addPart(cableItem, null, player);
            if (cable == null || Placement.isObstructed(level, pos, host)) {
                Placement.remove(host, cable);
                return false;
            }
        }

        if (!(partStack.getItem() instanceof IPartItem<?> partItem)) {
            Placement.remove(host, cable);
            return false;
        }

        IPart part = host.addPart(partItem, direction, player);
        if (part == null || Placement.isObstructed(level, pos, host)) {
            Placement.remove(host, part);
            if (!reuseCable) {
                Placement.remove(host, cable);
            }
            return false;
        }

        Placement.importPartSettings(part, partStack, player);
        if (Placement.isPlacementCanceled(snapshot, player)) {
            Placement.remove(host, part);
            if (!reuseCable) {
                Placement.remove(host, cable);
            }
            return false;
        }

        Placement.applyFrequencyAndUnitId(part, frequency, unitId);
        return true;
    }

    static boolean hasMarkedCable(IPartHost host, IPartItem<?> cableItem) {
        IPart cable = host == null ? null : host.getPart(null);
        return cable != null && cable.getPartItem().asItem() == cableItem.asItem();
    }

    private static void importPartSettings(IPart part, ItemStack partStack, ServerPlayer player) {
        try {
            part.importSettings(SettingsFrom.DISMANTLE_ITEM, partStack.getComponents(), player);
        } catch (Exception exception) {
            Ae2bcMod.LOGGER.warn("Failed to import part settings during component placement", exception);
        }
    }

    private static boolean isPlacementCanceled(BlockSnapshot snapshot, ServerPlayer player) {
        BlockEvent.EntityPlaceEvent placeEvent = new BlockEvent.EntityPlaceEvent(
                snapshot, snapshot.getState(), player);
        return NeoForge.EVENT_BUS.post(placeEvent).isCanceled();
    }

    private static void applyFrequencyAndUnitId(IPart part, short frequency, short unitId) {
        if (part instanceof P2PTunnelPart<?> tunnel && !(part instanceof Pp2mpInputPart)
                && frequency != 0) {
            IGrid grid = tunnel.getMainNode().getGrid();
            if (grid == null) {
                tunnel.setFrequency(frequency);
                tunnel.onTunnelNetworkChange();
            } else {
                P2PService.get(grid).updateFreq(tunnel, frequency);
            }
            tunnel.onTunnelConfigChange();
        }

        if (part instanceof Pp2mpUnitManagerPart manager) {
            short configuredUnitId = manager.getUnitId();
            manager.applyPlacerAttr(frequency, unitId == 0 ? configuredUnitId : unitId);
        } else if (part instanceof Pp2mpUnitPortPart unitPort) {
            unitPort.applyPlacerAttr(frequency, unitId);
        }
    }

    private static boolean isObstructed(ServerLevel level, BlockPos pos, IPartHost host) {
        VoxelShape shape = host.getCollisionShape(null);
        return !shape.isEmpty() && !level.isUnobstructed(null, shape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void remove(IPartHost host, IPart part) {
        if (part != null) {
            host.removePart(part);
        }
        if (host.isEmpty()) {
            host.cleanup();
        }
    }
}
