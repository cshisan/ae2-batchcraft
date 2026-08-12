package cn.ae2bc.logic;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Set;

import cn.ae2bc.core.extraction.ProductExtractionLimits;

/** Immutable extraction policy used by product extractors. */
public record ProductExtractionSettings(boolean enabled, int interval, int amount,
                                        boolean whitelist, Set<AEKey> markers) {
    public static final int DEFAULT_INTERVAL = ProductExtractionLimits.DEFAULT_INTERVAL;
    public static final int DEFAULT_AMOUNT = ProductExtractionLimits.DEFAULT_AMOUNT;
    public static final int MIN_INTERVAL = ProductExtractionLimits.MIN_INTERVAL;
    public static final int MAX_INTERVAL = ProductExtractionLimits.MAX_INTERVAL;
    public static final int MIN_AMOUNT = ProductExtractionLimits.MIN_AMOUNT;
    public static final int MAX_AMOUNT = ProductExtractionLimits.MAX_AMOUNT;
    public static final int MARKER_SLOT_COUNT = 18;

    public ProductExtractionSettings {
        interval = clampInterval(interval);
        amount = clampAmount(amount);
        markers = Set.copyOf(Objects.requireNonNull(markers, "markers"));
    }

    public static int clampInterval(int value) {
        return ProductExtractionLimits.clampInterval(value);
    }

    public static int clampAmount(int value) {
        return ProductExtractionLimits.clampAmount(value);
    }

    public boolean allows(ItemStack stack) {
        return allows(AEItemKey.of(stack));
    }

    public boolean allows(AEKey key) {
        if (key == null) {
            return false;
        }
        boolean marked = markers.contains(key);
        return whitelist ? marked : !marked;
    }
}
