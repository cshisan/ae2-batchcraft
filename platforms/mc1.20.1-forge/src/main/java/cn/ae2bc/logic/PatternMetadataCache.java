package cn.ae2bc.logic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

final class PatternMetadataCache {
    static final int MAX_ENTRIES = 64;

    private final BoundedLruCache<AEItemKey, PatternDispatchMetadata> entries =
            new BoundedLruCache<>(MAX_ENTRIES);

    PatternDispatchMetadata get(IPatternDetails pattern) {
        return entries.computeIfAbsent(pattern.getDefinition(), ignored -> PatternDispatchMetadata.create(pattern));
    }
}
