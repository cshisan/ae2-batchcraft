package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

/** Bounded per-input cache for decoded pattern dispatch metadata. */
final class PatternMetadataCache {
    private static final int MAX_ENTRIES = 64;

    private final BoundedLruCache<AEItemKey, PatternDispatchMetadata> entries =
            new BoundedLruCache<>(MAX_ENTRIES);

    PatternDispatchMetadata get(IPatternDetails pattern, net.minecraft.world.level.Level level) {
        return entries.computeIfAbsent(pattern.getDefinition(),
                ignored -> PatternDispatchMetadata.create(pattern, level));
    }
}
