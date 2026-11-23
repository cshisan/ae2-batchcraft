package org.ae2bc.part.task;

import appeng.api.stacks.AEKey;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.constant.Pp2mpTaskKeys;
import org.ae2bc.core.dispatch.TaskProgress;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** 一次完整交付的产物、票据和红石时钟，不能与下一批合并。 */
@Getter
public final class Pp2mpTaskRecord {
    private final UUID id;
    private final @Nullable AEKey primary;
    private @Setter @Nullable UUID converterId;
    private final TaskProgress<AEKey> progress = new TaskProgress<>();
    private @Setter long redstoneStart = Long.MIN_VALUE;

    public Pp2mpTaskRecord(UUID id, Map<AEKey, Long> outputs, @Nullable AEKey primary,
                           @Nullable UUID converterId) {
        this.id = id;
        this.primary = primary;
        this.converterId = converterId;
        this.progress.begin(outputs);
    }

    public boolean pending() {
        return this.progress.pending(this.primary);
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(Pp2mpTaskKeys.ID, this.id);
        if (this.converterId != null) {
            tag.putUUID(Pp2mpTaskKeys.CONVERTER, this.converterId);
        }
        if (this.primary != null) {
            tag.put(Pp2mpTaskKeys.PRIMARY, this.primary.toTagGeneric(registries));
        }
        tag.put(Pp2mpTaskKeys.DECLARED, MinecraftHelper.keyCountMap2listTag(this.progress.declared(), registries));
        tag.put(Pp2mpTaskKeys.REMAINING, MinecraftHelper.keyCountMap2listTag(this.progress.remaining(), registries));
        tag.putLong(Pp2mpTaskKeys.REDSTONE_START, this.redstoneStart);
        return tag;
    }

    public static @Nullable Pp2mpTaskRecord read(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(Pp2mpTaskKeys.ID)) {
            return null;
        }
        AEKey primary = tag.contains(Pp2mpTaskKeys.PRIMARY, Tag.TAG_COMPOUND)
                ? AEKey.fromTagGeneric(registries, tag.getCompound(Pp2mpTaskKeys.PRIMARY)) : null;
        UUID converter = tag.hasUUID(Pp2mpTaskKeys.CONVERTER) ? tag.getUUID(Pp2mpTaskKeys.CONVERTER) : null;
        Pp2mpTaskRecord record = new Pp2mpTaskRecord(tag.getUUID(Pp2mpTaskKeys.ID), Map.of(), primary, converter);
        MinecraftHelper.listTag2keyCountMap(tag, registries, Pp2mpTaskKeys.DECLARED, record.progress.declared());
        MinecraftHelper.listTag2keyCountMap(tag, registries, Pp2mpTaskKeys.REMAINING, record.progress.remaining());
        record.redstoneStart = tag.contains(Pp2mpTaskKeys.REDSTONE_START)
                ? tag.getLong(Pp2mpTaskKeys.REDSTONE_START) : Long.MIN_VALUE;
        return record;
    }
}
