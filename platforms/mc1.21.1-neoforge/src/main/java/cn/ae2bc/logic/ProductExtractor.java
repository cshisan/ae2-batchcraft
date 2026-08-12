package cn.ae2bc.logic;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.ae2bc.Ae2bcMod;

import java.util.Collection;
import java.util.Objects;

/** Moves filtered AE resources into a return inventory without extracting resources that cannot be stored. */
public final class ProductExtractor {
    @FunctionalInterface
    public interface OverflowHandler {
        void recover(GenericStack stack);
    }

    private ProductExtractor() {
    }

    public static int extract(Collection<ExtractionSource> sources, MEStorage destination,
                              ProductExtractionSettings settings, IActionSource actionSource,
                              OverflowHandler overflowHandler) {
        Objects.requireNonNull(overflowHandler, "overflowHandler");
        if (sources == null || destination == null || !(destination instanceof GenericInternalInventory internal)
                || !internal.canInsert()
                || !settings.enabled() || actionSource == null) {
            return 0;
        }

        int moved = 0;
        internal.beginBatch();
        try {
            for (var source : sources) {
                if (moved >= settings.amount()) {
                    break;
                }
                KeyCounter available = source.storage().getAvailableStacks();
                for (var entry : available) {
                    if (moved >= settings.amount()) {
                        break;
                    }
                    AEKey key = entry.getKey();
                    if (!settings.allows(key) || !source.supportedTypes().contains(key.getType())) {
                        continue;
                    }
                    long unit = Math.max(1L, key.getAmountPerOperation());
                    long remainingOperations = settings.amount() - moved;
                    long requested = Math.min(entry.getLongValue(),
                            saturatingMultiply(remainingOperations, unit));
                    if (requested <= 0) {
                        continue;
                    }

                    long accepted = Math.max(0, destination.insert(key, requested,
                            Actionable.SIMULATE, actionSource));
                    if (accepted <= 0) {
                        continue;
                    }
                    long extracted = Math.max(0, source.storage().extract(
                            key, Math.min(requested, accepted), Actionable.MODULATE, actionSource));
                    if (extracted <= 0) {
                        continue;
                    }
                    long inserted = Math.clamp(destination.insert(key, extracted,
                            Actionable.MODULATE, actionSource), 0, extracted);
                    moved = Math.min(settings.amount(), moved + operationsFor(inserted, unit));

                    long remainder = extracted - inserted;
                    if (remainder > 0) {
                        long restored = Math.max(0, source.storage().insert(
                                key, remainder, Actionable.MODULATE, actionSource));
                        long unrecovered = remainder - Math.min(restored, remainder);
                        if (unrecovered > 0) {
                            Ae2bcMod.LOGGER.error(
                                    "Storage rejected {} units of {} after an inconsistent extraction simulation",
                                    unrecovered, key);
                            overflowHandler.recover(new GenericStack(key, unrecovered));
                        }
                    }
                }
            }
        } finally {
            internal.endBatch();
        }
        return moved;
    }

    public static int extract(Collection<ExtractionSource> sources, RemoteReturnInventory destination,
                              ProductExtractionSettings settings, IActionSource actionSource,
                              OverflowHandler overflowHandler) {
        return extract(sources, (MEStorage) destination, settings, actionSource, overflowHandler);
    }

    private static int operationsFor(long amount, long unit) {
        if (amount <= 0) {
            return 0;
        }
        long operations = (amount - 1) / unit + 1;
        return (int) Math.min(Integer.MAX_VALUE, operations);
    }

    private static long saturatingMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
