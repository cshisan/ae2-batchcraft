package org.ae2bc.placer;

import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.me.helpers.PlayerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.placer.SelectionStatus;

import java.util.List;

public final class PlacementService {

    public static Result place(ServerPlayer player, ComponentPlacerMenuHost menuHost) {
        if (!(MinecraftHelper.level(player).get() instanceof ServerLevel level)) {
            return Result.empty();
        }

        ItemStack placer = menuHost.getItemStack();
        ComponentPlacerSelection selection = ComponentPlacerData.selection(placer);
        ComponentPlacerPose settings = ComponentPlacerData.pose(placer);
        ItemStack cable = ComponentPlacerData.markedCable(placer);
        ItemStack part = ComponentPlacerData.markedPart(placer);
        short frequency = ComponentPlacerData.frequency(placer);
        short unitId = ComponentPlacerData.unitId(placer);

        boolean validConfig = PlacementService.isValidConfig(level, selection, cable, part);
        if (!validConfig || !(cable.getItem() instanceof IPartItem<?> cableItem)) {
            return Result.empty();
        }

        List<BlockPos> positions = selection.positions(settings);
        PlayerSource source = new PlayerSource(player);
        if (ComponentPlacerItem.hasCraftingCard(placer) && menuHost.getLinkStatus().connected()) {
            PlacementMaterials.Requirements required = PlacementMaterials.countEligible(
                    level, player, positions, cableItem);
            PlacementMaterials.MissingMaterial missing = PlacementMaterials.findMissing(
                    menuHost, source, cable, required.cables());
            if (missing == null) {
                missing = PlacementMaterials.findMissing(menuHost, source, part, required.parts());
            }
            if (missing != null) {
                return Result.missing(missing.stack(), missing.amount());
            }
        }

        return PlacementService.executeBatch(
                level, player, menuHost, source, positions, cable,
                part, cableItem, settings, frequency, unitId
        );
    }

    private static boolean isValidConfig(
            ServerLevel level, ComponentPlacerSelection selection,
            ItemStack cable, ItemStack part
    ) {
        return selection != null
                && selection.dimension().equals(level.dimension().location())
                && selection.validate() == SelectionStatus.VALID
                && ComponentPlacerItem.isUsableCable(cable)
                && ComponentPlacerItem.isUsablePart(part);
    }

    private static Result executeBatch(
            ServerLevel level, ServerPlayer player, ComponentPlacerMenuHost menuHost,
            PlayerSource source, List<BlockPos> positions, ItemStack cable, ItemStack part,
            IPartItem<?> cableItem, ComponentPlacerPose settings, short frequency, short unitId
    ) {
        int placed = 0;
        int occupied = 0;
        int materialFailed = 0;
        int placementFailed = 0;

        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
                placementFailed++;
                continue;
            }

            IPartHost existingHost = PartHelper.getPartHost(level, pos);
            boolean reuseCable = Placement.hasMarkedCable(existingHost, cableItem);
            if (!reuseCable && !level.isEmptyBlock(pos)) {
                occupied++;
                continue;
            }

            PlacementMaterials.Reservation cableReservation = reuseCable ? null : PlacementMaterials.extract(menuHost, source, cable);
            PlacementMaterials.Reservation partReservation = PlacementMaterials.extract(menuHost, source, part);
            if ((!reuseCable && cableReservation == null) || partReservation == null) {
                PlacementService.refundReservations(
                        menuHost, player, source,
                        cableReservation, partReservation
                );
                materialFailed++;
                continue;
            }

            boolean placedAtPosition = Placement.placeAt(
                    level, player, pos, existingHost, cableItem, partReservation.stack(),
                    settings.direction(), frequency, unitId);
            if (placedAtPosition) {
                placed++;
            } else {
                PlacementService.refundReservations(
                        menuHost, player, source,
                        cableReservation, partReservation
                );
                placementFailed++;
            }
        }

        return new Result(placed, occupied, materialFailed, placementFailed, ItemStack.EMPTY, 0);
    }

    private static void refundReservations(
            ComponentPlacerMenuHost menuHost,
            ServerPlayer player, PlayerSource source,
            PlacementMaterials.Reservation cableReservation,
            PlacementMaterials.Reservation partReservation
    ) {
        if (partReservation != null) {
            PlacementMaterials.refund(menuHost, player, source, partReservation);
        }
        if (cableReservation != null) {
            PlacementMaterials.refund(menuHost, player, source, cableReservation);
        }
    }

    public record Result(
            int placed, int occupied,
            int materialFailed, int placementFailed,
            ItemStack missingMaterial, int missingAmount
    ) {
        public static Result empty() {
            return new Result(0, 0, 0, 0, ItemStack.EMPTY, 0);
        }

        private static Result missing(ItemStack stack, int amount) {
            return new Result(0, 0, 0, 0, stack, amount);
        }
    }
}
