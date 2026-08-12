package cn.ae2bc.placer;

import appeng.core.Api;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEPartLocation;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.me.cache.P2PCache;
import cn.ae2bc.menu.ComponentPlacerMenu;
import cn.ae2bc.part.PatternP2PTunnelPart;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public final class ComponentPlacementService {
    private ComponentPlacementService() { }

    public static Result place(ServerPlayerEntity player, ComponentPlacerMenu menu) {
        if (!(player.level instanceof ServerWorld)) return Result.empty();
        ServerWorld world = (ServerWorld) player.level;
        Hand hand = menu.getHand();
        ItemStack placer = menu.getPlacer();
        IItemHandler materials = menu.getMaterials();
        ComponentPlacerNetworkAccess network = menu.getNetworkAccess();
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(placer);
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(placer);
        ItemStack cable = ComponentPlacerItem.getMarkedCable(placer);
        ItemStack part = ComponentPlacerItem.getMarkedPart(placer);
        if (selection == null || !selection.getDimension().equals(world.dimension().location())
                || selection.validate() != ComponentPlacerSelection.SelectionValidation.VALID
                || !ComponentPlacerItem.isUsableCable(cable) || !ComponentPlacerItem.isUsablePart(part)) {
            return Result.empty();
        }
        List<BlockPos> positions = selection.positions(settings);
        if (positions.isEmpty()) return Result.empty();

        boolean connected = network != null && network.isConnected();
        int required = countEligible(world, player, positions, settings.getDirection());
        if (connected && ComponentPlacerItem.hasCraftingCard(placer)) {
            int missingCable = missing(network, materials, cable, required);
            if (missingCable > 0) return Result.missing(cable, missingCable);
            int missingPart = missing(network, materials, part, required);
            if (missingPart > 0) return Result.missing(part, missingPart);
        }

        int placed = 0;
        int occupied = 0;
        int materialFailed = 0;
        int placementFailed = 0;
        for (BlockPos pos : positions) {
            if (!world.hasChunkAt(pos) || !world.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, settings.getDirection(), placer)) {
                placementFailed++;
                continue;
            }
            if (!world.isEmptyBlock(pos)) {
                occupied++;
                continue;
            }
            Extraction cableUse = extract(network, connected, materials, cable);
            if (!cableUse.isSuccess()) {
                if (cableUse.failureReason != FailureReason.MATERIAL) {
                    return Result.failure(placed, occupied, materialFailed, placementFailed,
                            cableUse.failureReason);
                }
                materialFailed++;
                continue;
            }
            Extraction partUse = extract(network, connected, materials, part);
            if (!partUse.isSuccess()) {
                refund(player, network, materials, cableUse.reservation);
                if (partUse.failureReason != FailureReason.MATERIAL) {
                    return Result.failure(placed, occupied, materialFailed, placementFailed,
                            partUse.failureReason);
                }
                materialFailed++;
                continue;
            }
            if (placeAt(world, player, hand, pos, cableUse.reservation.stack, partUse.reservation.stack,
                    settings.getDirection(), ComponentPlacerItem.getFrequency(placer))) {
                placed++;
            } else {
                refund(player, network, materials, partUse.reservation);
                refund(player, network, materials, cableUse.reservation);
                placementFailed++;
            }
        }
        return new Result(placed, occupied, materialFailed, placementFailed,
                ItemStack.EMPTY, 0, FailureReason.NONE);
    }

    private static int countEligible(ServerWorld world, ServerPlayerEntity player, List<BlockPos> positions,
                                     Direction direction) {
        int result = 0;
        for (BlockPos pos : positions) {
            if (world.hasChunkAt(pos) && world.mayInteract(player, pos)
                    && player.mayUseItemAt(pos, direction, ItemStack.EMPTY) && world.isEmptyBlock(pos)) result++;
        }
        return result;
    }

    private static boolean placeAt(ServerWorld world, ServerPlayerEntity player, Hand hand, BlockPos pos,
                                   ItemStack cable, ItemStack part, Direction direction, short frequency) {
        BlockSnapshot snapshot = BlockSnapshot.create(world.dimension(), world, pos);
        BlockState state = Api.instance().definitions().blocks().multiPart().block().defaultBlockState();
        if (!world.setBlock(pos, state, 3)) return false;
        if (ForgeEventFactory.onBlockPlace(player, snapshot, direction)) {
            snapshot.restore(true, false);
            return false;
        }
        if (!(world.getBlockEntity(pos) instanceof IPartHost)) {
            snapshot.restore(true, false);
            return false;
        }
        IPartHost host = (IPartHost) world.getBlockEntity(pos);
        AEPartLocation partSide = AEPartLocation.fromFacing(direction);
        if (!host.canAddPart(cable, AEPartLocation.INTERNAL)
                || host.addPart(cable, AEPartLocation.INTERNAL, player, hand) == null
                || !host.canAddPart(part, partSide)
                || host.addPart(part, partSide, player, hand) == null) {
            snapshot.restore(true, false);
            return false;
        }
        IPart placedPart = host.getPart(partSide);
        if (frequency != 0 && placedPart instanceof P2PTunnelPart
                && (!(placedPart instanceof PatternP2PTunnelPart)
                || ((PatternP2PTunnelPart) placedPart).isOutput())) {
            P2PTunnelPart<?> tunnel = (P2PTunnelPart<?>) placedPart;
            tunnel.setFrequency(frequency);
            if (tunnel.getGridNode() != null && tunnel.getGridNode().getGrid() != null) {
                ((P2PCache) tunnel.getGridNode().getGrid().getCache(P2PCache.class))
                        .updateFreq(tunnel, frequency);
            }
            tunnel.onTunnelNetworkChange();
            tunnel.onTunnelConfigChange();
        }
        host.markForSave();
        host.markForUpdate();
        return true;
    }

    private static int missing(ComponentPlacerNetworkAccess network,
                               IItemHandler materials, ItemStack requested, int required) {
        long available = countLocal(materials, requested);
        if (network != null) available += network.count(requested, required);
        return available >= required ? 0 : (int) (required - available);
    }

    private static long countLocal(IItemHandler materials, ItemStack requested) {
        long result = 0;
        for (int i = 0; i < materials.getSlots(); i++) {
            ItemStack stack = materials.getStackInSlot(i);
            if (ItemStack.isSame(stack, requested) && ItemStack.tagMatches(stack, requested)) result += stack.getCount();
        }
        return result;
    }

    private static Extraction extract(ComponentPlacerNetworkAccess network, boolean networkExpected,
                                      IItemHandler materials, ItemStack requested) {
        boolean networkConnected = network != null && network.isConnected();
        boolean networkHasItem = networkConnected && network.count(requested, 1) == 1;
        if (networkHasItem) {
            ItemStack extracted = network.extractPowered(requested, 1);
            if (!extracted.isEmpty() && extracted.getCount() == 1) {
                return Extraction.success(new Reservation(extracted, true));
            }
        }
        for (int i = 0; i < materials.getSlots(); i++) {
            ItemStack stack = materials.getStackInSlot(i);
            if (ItemStack.isSame(stack, requested) && ItemStack.tagMatches(stack, requested)) {
                ItemStack extracted = materials.extractItem(i, 1, false);
                if (!extracted.isEmpty()) return Extraction.success(new Reservation(extracted, false));
            }
        }
        if (networkHasItem) return Extraction.failure(FailureReason.POWER);
        if (networkExpected && !networkConnected) return Extraction.failure(FailureReason.NETWORK);
        return Extraction.failure(FailureReason.MATERIAL);
    }

    private static void refund(ServerPlayerEntity player, ComponentPlacerNetworkAccess network,
                               IItemHandler materials, Reservation reservation) {
        ItemStack refund = reservation.stack.copy();
        if (reservation.network && network != null) {
            refund = network.insertPowered(refund);
            if (refund.isEmpty()) return;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(materials, refund, false);
        if (!remainder.isEmpty() && !player.inventory.add(remainder)) player.drop(remainder, false);
    }

    public enum FailureReason {
        NONE,
        MATERIAL,
        POWER,
        NETWORK
    }

    private static final class Extraction {
        private final Reservation reservation;
        private final FailureReason failureReason;

        private Extraction(Reservation reservation, FailureReason failureReason) {
            this.reservation = reservation;
            this.failureReason = failureReason;
        }

        private static Extraction success(Reservation reservation) {
            return new Extraction(reservation, FailureReason.NONE);
        }

        private static Extraction failure(FailureReason failureReason) {
            return new Extraction(null, failureReason);
        }

        private boolean isSuccess() { return reservation != null; }
    }

    private static final class Reservation {
        private final ItemStack stack;
        private final boolean network;
        private Reservation(ItemStack stack, boolean network) { this.stack = stack; this.network = network; }
    }

    public static final class Result {
        private final int placed;
        private final int occupied;
        private final int materialFailed;
        private final int placementFailed;
        private final ItemStack missingMaterial;
        private final int missingAmount;
        private final FailureReason failureReason;
        private Result(int placed, int occupied, int materialFailed, int placementFailed,
                       ItemStack missingMaterial, int missingAmount, FailureReason failureReason) {
            this.placed = placed; this.occupied = occupied; this.materialFailed = materialFailed;
            this.placementFailed = placementFailed; this.missingMaterial = missingMaterial;
            this.missingAmount = missingAmount; this.failureReason = failureReason;
        }
        public static Result empty() {
            return new Result(0, 0, 0, 0, ItemStack.EMPTY, 0, FailureReason.NONE);
        }
        public static Result missing(ItemStack stack, int amount) {
            ItemStack copy = stack.copy(); copy.setCount(1);
            return new Result(0, 0, 0, 0, copy, amount, FailureReason.NONE);
        }
        public static Result failure(int placed, int occupied, int materialFailed, int placementFailed,
                                     FailureReason reason) {
            return new Result(placed, occupied, materialFailed, placementFailed,
                    ItemStack.EMPTY, 0, reason);
        }
        public int getPlaced() { return placed; }
        public int getOccupied() { return occupied; }
        public int getMaterialFailed() { return materialFailed; }
        public int getPlacementFailed() { return placementFailed; }
        public ItemStack getMissingMaterial() { return missingMaterial; }
        public int getMissingAmount() { return missingAmount; }
        public FailureReason getFailureReason() { return failureReason; }
    }
}
