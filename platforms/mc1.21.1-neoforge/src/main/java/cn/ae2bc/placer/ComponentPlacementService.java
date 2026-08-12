package cn.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;
import cn.ae2bc.part.PatternP2PTunnelPart;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Objects;

/** Server-side implementation of the placer. One target is an all-or-nothing operation. */
public final class ComponentPlacementService {
    private ComponentPlacementService() {
    }

    public static Result place(ServerPlayer player, ComponentPlacerMenuHost menuHost) {
        if (!(player.level() instanceof ServerLevel level)) {
            return Result.empty();
        }

        ItemStack placer = menuHost.getItemStack();
        ComponentPlacerSelection selection = placer.get(ModContent.PLACER_SELECTION.get());
        ComponentPlacerSettings settings = placer.getOrDefault(
                ModContent.PLACER_SETTINGS.get(), ComponentPlacerSettings.DEFAULT);
        ItemStack cable = ComponentPlacerItem.getMarkedCable(placer);
        ItemStack part = ComponentPlacerItem.getMarkedPart(placer);
        short frequency = placer.getOrDefault(ModContent.PLACER_FREQUENCY.get(), (short) 0);

        if (selection == null || !selection.dimension().equals(level.dimension().location())
                || !ComponentPlacerItem.isUsableCable(cable)
                || !ComponentPlacerItem.isUsablePart(part)) {
            return Result.empty();
        }
        if (selection.validate() != ComponentPlacerSelection.Validation.VALID) {
            return Result.empty();
        }
        var positions = selection.positions(settings);
        if (positions.isEmpty()) {
            return Result.empty();
        }

        IPartItem<?> cableItem = (IPartItem<?>) cable.getItem();
        IPartItem<?> partItem = (IPartItem<?>) part.getItem();
        PlayerSource actionSource = new PlayerSource(player);

        int required = countEligibleTargets(level, player, positions);
        if (ComponentPlacerItem.hasCraftingCard(placer) && menuHost.getLinkStatus().connected()) {
            MissingMaterial cableMissing = findMissingMaterial(menuHost, actionSource, cable, required);
            if (cableMissing != null) {
                return Result.missing(cableMissing.stack(), cableMissing.amount());
            }
            MissingMaterial partMissing = findMissingMaterial(menuHost, actionSource, part, required);
            if (partMissing != null) {
                return Result.missing(partMissing.stack(), partMissing.amount());
            }
        }

        int placed = 0;
        int occupied = 0;
        int materialFailed = 0;
        int placementFailed = 0;
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
                placementFailed++;
                continue;
            }
            if (!level.isEmptyBlock(pos)) {
                occupied++;
                continue;
            }

            Reservation cableReservation = extract(menuHost, actionSource, cable);
            if (cableReservation == null) {
                materialFailed++;
                continue;
            }

            Reservation partReservation = extract(menuHost, actionSource, part);
            if (partReservation == null) {
                refund(menuHost, player, actionSource, cableReservation);
                materialFailed++;
                continue;
            }

            if (placeAt(level, player, pos, cableItem, partItem, settings.direction(), frequency)) {
                placed++;
            } else {
                refund(menuHost, player, actionSource, partReservation);
                refund(menuHost, player, actionSource, cableReservation);
                placementFailed++;
            }
        }

        return new Result(placed, occupied, materialFailed, placementFailed, ItemStack.EMPTY, 0);
    }

    private static boolean placeAt(ServerLevel level, ServerPlayer player, BlockPos pos,
                                    IPartItem<?> cableItem, IPartItem<?> partItem, Direction direction,
                                   short frequency) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        IPartHost host = PartHelper.getOrPlacePartHost(level, pos, false, player);
        if (host == null) {
            return false;
        }

        IPart cablePart = addPart(host, cableItem, null, player);
        if (cablePart == null || !isUnobstructed(level, pos, host)) {
            removePart(host, cablePart);
            return false;
        }

        IPart placedPart = addPart(host, partItem, direction, player);
        if (placedPart == null || !isUnobstructed(level, pos, host)) {
            removePart(host, placedPart);
            removePart(host, cablePart);
            return false;
        }
        var placeEvent = new BlockEvent.EntityPlaceEvent(snapshot, snapshot.getState(), player);
        NeoForge.EVENT_BUS.post(placeEvent);
        if (placeEvent.isCanceled()) {
            removePart(host, placedPart);
            removePart(host, cablePart);
            return false;
        }
        if (frequency != 0 && placedPart instanceof P2PTunnelPart<?> tunnel
                && (!(tunnel instanceof PatternP2PTunnelPart patternTunnel)
                || patternTunnel.isOutput())) {
            var grid = tunnel.getMainNode().getGrid();
            if (grid == null) {
                tunnel.setFrequency(frequency);
                tunnel.onTunnelNetworkChange();
            } else {
                P2PService.get(grid).updateFreq(tunnel, frequency);
            }
            tunnel.onTunnelConfigChange();
        }
        return true;
    }

    private static int countEligibleTargets(ServerLevel level, ServerPlayer player, java.util.List<BlockPos> positions) {
        int result = 0;
        for (BlockPos pos : positions) {
            if (level.isLoaded(pos) && level.mayInteract(player, pos) && level.isEmptyBlock(pos)) {
                result++;
            }
        }
        return result;
    }

    private static MissingMaterial findMissingMaterial(ComponentPlacerMenuHost host, PlayerSource actionSource,
                                                       ItemStack requested, int required) {
        if (required <= 0) {
            return null;
        }

        long available = countLocal(host.getMaterials(), requested);
        if (host.getLinkStatus().connected()) {
            AEItemKey key = AEItemKey.of(requested);
            if (key != null) {
                available += StorageHelper.poweredExtraction(host, host.getInventory(), key, required,
                        actionSource, Actionable.SIMULATE);
            }
        }
        return available < required ? new MissingMaterial(requested.copyWithCount(1), (int) (required - available)) : null;
    }

    private static long countLocal(InternalInventory inventory, ItemStack requested) {
        long available = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(stack, requested)) {
                available += stack.getCount();
            }
        }
        return available;
    }

    private static IPart addPart(IPartHost host, IPartItem<?> partItem, Direction side, ServerPlayer player) {
        return host.addPart(partItem, side, player);
    }

    private static boolean isUnobstructed(ServerLevel level, BlockPos pos, IPartHost host) {
        VoxelShape shape = host.getCollisionShape(null);
        return shape.isEmpty() || level.isUnobstructed(null, shape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void removePart(IPartHost host, IPart part) {
        if (part != null) {
            host.removePart(part);
        }
        if (host.isEmpty()) {
            host.cleanup();
        }
    }

    private static Reservation extract(ComponentPlacerMenuHost host, PlayerSource actionSource, ItemStack requested) {
        if (requested.isEmpty()) {
            return null;
        }

        if (host.getLinkStatus().connected()) {
            AEItemKey key = AEItemKey.of(requested);
            if (key != null && StorageHelper.poweredExtraction(
                    host, host.getInventory(), key, 1, actionSource, Actionable.MODULATE) == 1) {
                return new Reservation(key.toStack(), Source.AE);
            }
        }

        ItemStack local = host.getMaterials().removeItems(1, requested, null);
        if (!local.isEmpty()) {
            return new Reservation(local.copyWithCount(1), Source.LOCAL);
        }
        return null;
    }

    private static void refund(ComponentPlacerMenuHost host, ServerPlayer player, PlayerSource actionSource,
                               Reservation reservation) {
        ItemStack refund = reservation.stack().copyWithCount(1);
        if (reservation.source() == Source.AE) {
            AEItemKey key = AEItemKey.of(refund);
            if (key != null && StorageHelper.poweredInsert(
                    host, host.getInventory(), key, 1, actionSource, Actionable.MODULATE) == 1) {
                return;
            }
        } else {
            ItemStack remainder = host.getMaterials().addItems(refund);
            if (remainder.isEmpty()) {
                return;
            }
            refund = remainder;
        }

        // A full local inventory or a network that went offline must never destroy a reservation.
        player.getInventory().placeItemBackInInventory(refund);
    }

    private enum Source {
        AE,
        LOCAL
    }

    private record Reservation(ItemStack stack, Source source) {
        private Reservation {
            Objects.requireNonNull(stack);
            Objects.requireNonNull(source);
        }
    }

    private record MissingMaterial(ItemStack stack, int amount) {
    }

    public record Result(int placed, int occupied, int materialFailed, int placementFailed,
                         ItemStack missingMaterial, int missingAmount) {
        public static Result empty() {
            return new Result(0, 0, 0, 0, ItemStack.EMPTY, 0);
        }

        public static Result missing(ItemStack stack, int amount) {
            return new Result(0, 0, 0, 0, stack, amount);
        }
    }
}
