package cn.ae2bc.placer;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.me.cache.P2PCache;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public final class ComponentPlacementService {
    private ComponentPlacementService() { }
    public static Result place(EntityPlayerMP player, ComponentPlacerMenu menu) {
        if (!(player.world instanceof WorldServer)) return Result.empty();
        WorldServer world = (WorldServer) player.world;
        EnumHand hand = menu.getHand();
        ItemStack placer = menu.getPlacer();
        IItemHandler materials = menu.getMaterials();
        ComponentPlacerNetworkAccess network = menu.getNetworkAccess();
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(placer);
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(placer);
        ItemStack cable = ComponentPlacerItem.getMarkedCable(placer), part = ComponentPlacerItem.getMarkedPart(placer);
        if (selection == null || selection.getDimension() != world.provider.getDimension()
                || selection.validate() != ComponentPlacerSelection.SelectionValidation.VALID
                || !ComponentPlacerItem.isUsableCable(cable) || !ComponentPlacerItem.isUsablePart(part)) return Result.empty();
        List<BlockPos> positions = selection.positions(settings); if (positions.isEmpty()) return Result.empty();
        boolean connected = network != null && network.isConnected();
        int required = countEligible(world, player, positions, settings.getDirection(), placer);
        if (connected && ComponentPlacerItem.hasCraftingCard(placer)) {
            int missing = missing(network, materials, cable, required);
            if (missing > 0) return Result.missing(cable, missing);
            missing = missing(network, materials, part, required);
            if (missing > 0) return Result.missing(part, missing);
        }
        int placed = 0, occupied = 0, materialFailed = 0, placementFailed = 0;
        for (BlockPos pos : positions) {
            if (!world.isBlockLoaded(pos) || !world.isBlockModifiable(player, pos)
                    || !player.canPlayerEdit(pos, settings.getDirection(), placer)) { placementFailed++; continue; }
            if (!world.isAirBlock(pos)) { occupied++; continue; }
            Extraction cableUse = extract(network, connected, materials, cable);
            if (!cableUse.isSuccess()) {
                if (cableUse.failureReason != FailureReason.MATERIAL)
                    return Result.failure(placed, occupied, materialFailed, placementFailed,
                            cableUse.failureReason);
                materialFailed++; continue;
            }
            Extraction partUse = extract(network, connected, materials, part);
            if (!partUse.isSuccess()) {
                refund(player, network, materials, cableUse.reservation);
                if (partUse.failureReason != FailureReason.MATERIAL)
                    return Result.failure(placed, occupied, materialFailed, placementFailed,
                            partUse.failureReason);
                materialFailed++; continue;
            }
            if (placeAt(world, player, hand, pos, cableUse.reservation.stack, partUse.reservation.stack,
                    settings.getDirection(), ComponentPlacerItem.getFrequency(placer))) placed++;
            else { refund(player, network, materials, partUse.reservation);
                refund(player, network, materials, cableUse.reservation); placementFailed++; }
        }
        return new Result(placed, occupied, materialFailed, placementFailed,
                ItemStack.EMPTY, 0, FailureReason.NONE);
    }
    private static int countEligible(WorldServer world, EntityPlayerMP player, List<BlockPos> positions,
                                     EnumFacing direction, ItemStack placer) {
        int result = 0; for (BlockPos pos : positions)
            if (world.isBlockLoaded(pos) && world.isBlockModifiable(player, pos)
                    && player.canPlayerEdit(pos, direction, placer) && world.isAirBlock(pos)) result++;
        return result;
    }
    private static boolean placeAt(WorldServer world, EntityPlayerMP player, EnumHand hand, BlockPos pos,
                                   ItemStack cable, ItemStack part, EnumFacing direction, short frequency) {
        BlockSnapshot snapshot = BlockSnapshot.getBlockSnapshot(world, pos);
        if (!world.setBlockState(pos, AEApi.instance().definitions().blocks().multiPart()
                .maybeBlock().get().getDefaultState(), 3)) return false;
        if (ForgeEventFactory.onPlayerBlockPlace(player, snapshot, direction, hand).isCanceled()) {
            snapshot.restore(true, false); return false;
        }
        if (!(world.getTileEntity(pos) instanceof IPartHost)) { snapshot.restore(true, false); return false; }
        IPartHost host = (IPartHost) world.getTileEntity(pos); AEPartLocation side = AEPartLocation.fromFacing(direction);
        if (!host.canAddPart(cable, AEPartLocation.INTERNAL)
                || host.addPart(cable, AEPartLocation.INTERNAL, player, hand) == null
                || !host.canAddPart(part, side) || host.addPart(part, side, player, hand) == null) {
            snapshot.restore(true, false); return false;
        }
        IPart placedPart = host.getPart(side);
        if (frequency != 0 && placedPart instanceof PartP2PTunnel
                && (!(placedPart instanceof PatternP2PTunnelPart) || ((PatternP2PTunnelPart) placedPart).isOutput())) {
            PartP2PTunnel<?> tunnel = (PartP2PTunnel<?>) placedPart;
            tunnel.setFrequency(frequency);
            if (tunnel.getGridNode() != null && tunnel.getGridNode().getGrid() != null)
                ((P2PCache) tunnel.getGridNode().getGrid().getCache(P2PCache.class))
                        .updateFreq(tunnel, frequency);
            tunnel.onTunnelNetworkChange(); tunnel.onTunnelConfigChange();
        }
        host.markForSave(); host.markForUpdate(); return true;
    }
    private static int missing(ComponentPlacerNetworkAccess network,
                               IItemHandler materials, ItemStack requested, int required) {
        long available = countLocal(materials, requested);
        if (network != null) available += network.count(requested, required);
        return available >= required ? 0 : (int) (required - available);
    }
    private static long countLocal(IItemHandler inventory, ItemStack requested) {
        long result = 0; for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (ItemStack.areItemsEqual(stack, requested) && ItemStack.areItemStackTagsEqual(stack, requested)) result += stack.getCount();
        } return result;
    }
    private static Extraction extract(ComponentPlacerNetworkAccess network, boolean networkExpected,
                                      IItemHandler inventory, ItemStack requested) {
        boolean networkConnected = network != null && network.isConnected();
        boolean networkHasItem = networkConnected && network.count(requested, 1) == 1;
        if (networkHasItem) {
            ItemStack value = network.extractPowered(requested, 1);
            if (!value.isEmpty() && value.getCount() == 1)
                return Extraction.success(new Reservation(value, true));
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (ItemStack.areItemsEqual(stack, requested) && ItemStack.areItemStackTagsEqual(stack, requested)) {
                ItemStack value = inventory.extractItem(i, 1, false);
                if (!value.isEmpty()) return Extraction.success(new Reservation(value, false));
            }
        }
        if (networkHasItem) return Extraction.failure(FailureReason.POWER);
        if (networkExpected && !networkConnected) return Extraction.failure(FailureReason.NETWORK);
        return Extraction.failure(FailureReason.MATERIAL);
    }
    private static void refund(EntityPlayerMP player, ComponentPlacerNetworkAccess network,
                               IItemHandler materials, Reservation reservation) {
        ItemStack refund = reservation.stack.copy();
        if (reservation.network && network != null) {
            refund = network.insertPowered(refund);
            if (refund.isEmpty()) return;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(materials, refund, false);
        if (!remainder.isEmpty() && !player.inventory.addItemStackToInventory(remainder)) player.dropItem(remainder, false);
    }
    public enum FailureReason { NONE, MATERIAL, POWER, NETWORK }
    private static final class Extraction {
        private final Reservation reservation; private final FailureReason failureReason;
        private Extraction(Reservation reservation, FailureReason failureReason) {
            this.reservation = reservation; this.failureReason = failureReason;
        }
        private static Extraction success(Reservation reservation) {
            return new Extraction(reservation, FailureReason.NONE);
        }
        private static Extraction failure(FailureReason reason) { return new Extraction(null, reason); }
        private boolean isSuccess() { return reservation != null; }
    }
    private static final class Reservation {
        private final ItemStack stack; private final boolean network;
        private Reservation(ItemStack stack, boolean network) { this.stack = stack; this.network = network; }
    }
    public static final class Result {
        private final int placed, occupied, materialFailed, placementFailed, missingAmount;
        private final ItemStack missingMaterial;
        private final FailureReason failureReason;
        private Result(int p, int o, int m, int f, ItemStack stack, int amount, FailureReason reason) {
            placed = p; occupied = o; materialFailed = m; placementFailed = f;
            missingMaterial = stack; missingAmount = amount; failureReason = reason;
        }
        public static Result empty() {
            return new Result(0, 0, 0, 0, ItemStack.EMPTY, 0, FailureReason.NONE);
        }
        public static Result missing(ItemStack stack, int amount) {
            ItemStack copy = stack.copy(); copy.setCount(1);
            return new Result(0, 0, 0, 0, copy, amount, FailureReason.NONE);
        }
        public static Result failure(int p, int o, int m, int f, FailureReason reason) {
            return new Result(p, o, m, f, ItemStack.EMPTY, 0, reason);
        }
        public int getPlaced() { return placed; } public int getOccupied() { return occupied; }
        public int getMaterialFailed() { return materialFailed; } public int getPlacementFailed() { return placementFailed; }
        public ItemStack getMissingMaterial() { return missingMaterial; } public int getMissingAmount() { return missingAmount; }
        public FailureReason getFailureReason() { return failureReason; }
    }
}
