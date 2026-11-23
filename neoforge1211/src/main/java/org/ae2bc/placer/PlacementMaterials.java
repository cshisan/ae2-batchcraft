package org.ae2bc.placer;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.core.placer.MaterialsMode;

import java.util.List;
import java.util.Objects;

/**
 * 负责部件放置过程中的材料检查、预留和退还。
 */
final class PlacementMaterials {

    /* 统计本批次中会消耗线缆或部件的放置位置。 */
    static Requirements countEligible(ServerLevel level, ServerPlayer player,
                                      List<BlockPos> positions, IPartItem<?> cableItem) {
        int cables = 0;
        int parts = 0;
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
                continue;
            }
            if (level.isEmptyBlock(pos)) {
                cables++;
                parts++;
            } else {
                IPartHost host = PartHelper.getPartHost(level, pos);
                if (Placement.hasMarkedCable(host, cableItem)) {
                    parts++;
                }
            }
        }
        return new Requirements(cables, parts);
    }

    record Requirements(int cables, int parts) {
    }

    /* 在修改任一来源前，模拟本地和网络材料的可用数量。 */
    static MissingMaterial findMissing(
            ComponentPlacerMenuHost host, PlayerSource source,
            ItemStack requested, int amount
    ) {
        if (amount <= 0) {
            return null;
        }

        long available = 0;
        InternalInventory local = host.getMaterials();
        for (int slot = 0; slot < local.size(); slot++) {
            ItemStack stack = local.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, requested)) {
                available += stack.getCount();
            }
        }

        AEItemKey key = AEItemKey.of(requested);
        if (key != null) {
            available += StorageHelper.poweredExtraction(
                    host, host.getInventory(), key, amount, source, Actionable.SIMULATE);
        }
        return available >= amount ? null : new MissingMaterial(
                requested.copyWithCount(1),
                (int) (amount - available)
        );
    }

    /* 优先从已连接网络预留一个所需部件，再从放置器本地库存预留。 */
    static Reservation extract(ComponentPlacerMenuHost host, PlayerSource source, ItemStack requested) {
        AEItemKey key = AEItemKey.of(requested);
        if (key != null && host.getLinkStatus().connected()) {
            long powereded = StorageHelper.poweredExtraction(
                    host, host.getInventory(), key, 1, source, Actionable.MODULATE
            );
            if (powereded == 1) {
                return new Reservation(key.toStack(), MaterialsMode.NETWORK);
            }
        }

        ItemStack local = host.getMaterials().removeItems(1, requested, null);
        return local.isEmpty() ? null : new Reservation(
                local.copyWithCount(1),
                MaterialsMode.LOCAL
        );
    }

    /* 将失败的预留退回原始来源，最后退回玩家物品栏。 */
    static void refund(ComponentPlacerMenuHost host, ServerPlayer player, PlayerSource source,
                       Reservation reservation) {
        if (reservation.source() == MaterialsMode.NETWORK) {
            AEItemKey key = AEItemKey.of(reservation.stack());
            if (key != null && StorageHelper.poweredInsert(
                    host, host.getInventory(), key, 1, source, Actionable.MODULATE) == 1) {
                return;
            }
        } else if (host.getMaterials().addItems(reservation.stack()).isEmpty()) {
            return;
        }
        player.getInventory().placeItemBackInInventory(reservation.stack());
    }

    record Reservation(ItemStack stack, MaterialsMode source) {
        Reservation {
            Objects.requireNonNull(stack);
            Objects.requireNonNull(source);
        }
    }

    record MissingMaterial(ItemStack stack, int amount) {
    }
}
