package org.ae2bc.part.unit.manager;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.ae2bc.core.unit.OutputPortMode;
import org.ae2bc.storage.PendingMaterial;
import org.jspecify.annotations.Nullable;

@Getter
@EqualsAndHashCode
public final class Pp2mpPortAssignment {
    private final OutputPortMode mode;
    private int slot = -1;
    private @Nullable AEKey key;
    private long delivered;

    public Pp2mpPortAssignment(OutputPortMode mode) {
        this.mode = mode;
    }

    /* 模拟和实际投料共用同一份任务限额规则。 */
    public long requested(PendingMaterial material, long remaining) {
        AEKey candidate = material.stack().what();
        return switch (this.mode) {
            case NORMAL -> remaining;
            case SLOT_EXCLUSIVE -> this.slot < 0 || this.slot == material.slot() ? remaining : 0;
            case SAME_TYPE -> this.key == null || this.key.equals(candidate) ? remaining : 0;
            case SINGLE -> {
                if (this.key != null && !this.key.equals(candidate)) {
                    yield 0;
                }
                long limit = candidate instanceof AEFluidKey
                        ? AEFluidKey.AMOUNT_BUCKET : Math.max(1L, candidate.getAmountPerOperation());
                yield Math.min(remaining, Math.max(0, limit - this.delivered));
            }
        };
    }

    /* 空槽仅占用尚未绑定材料槽的独占端口。 */
    public boolean reserveEmpty(int slot) {
        if (this.mode != OutputPortMode.SLOT_EXCLUSIVE || this.slot >= 0) {
            return false;
        }
        this.slot = slot;
        return true;
    }

    /* 预检记录分配额度，实际投料仅按成功数量记账。 */
    public void record(PendingMaterial material, long amount) {
        switch (this.mode) {
            case SLOT_EXCLUSIVE -> this.slot = material.slot();
            case SAME_TYPE -> this.key = material.stack().what();
            case SINGLE -> {
                this.key = material.stack().what();
                this.delivered += amount;
            }
            case NORMAL -> { }
        }
    }

    /* 保留预检绑定，但实际发送量从零开始。 */
    public void beginDelivery() {
        this.delivered = 0;
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", this.mode.id);
        tag.putInt("Slot", this.slot);
        tag.putLong("Delivered", this.delivered);
        if (this.key != null) {
            tag.put("Key", this.key.toTagGeneric(registries));
        }
        return tag;
    }

    public static Pp2mpPortAssignment read(CompoundTag tag, HolderLookup.Provider registries) {
        Pp2mpPortAssignment assignment = new Pp2mpPortAssignment(OutputPortMode.null2defaultOf(tag.getInt("Mode")));
        assignment.slot = tag.contains("Slot") ? tag.getInt("Slot") : -1;
        assignment.delivered = Math.max(0, tag.getLong("Delivered"));
        assignment.key = tag.contains("Key") ? AEKey.fromTagGeneric(registries, tag.getCompound("Key")) : null;
        return assignment;
    }
}
