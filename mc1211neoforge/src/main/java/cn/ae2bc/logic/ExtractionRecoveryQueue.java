package cn.ae2bc.logic;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Durable queue for resources that could not be restored after extraction. */
public final class ExtractionRecoveryQueue {
    private final RecoveryBuffer<AEKey> buffer = new RecoveryBuffer<>();
    private final Runnable changeListener;

    public ExtractionRecoveryQueue(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public void queue(GenericStack stack) {
        if (stack == null || stack.amount() <= 0) {
            return;
        }
        buffer.queue(stack.what(), stack.amount());
        changeListener.run();
    }

    public boolean drain(BiFunction<AEKey, Long, Long> inserter) {
        boolean changed = buffer.drain(inserter);
        if (changed) {
            changeListener.run();
        }
        return changed;
    }

    public void read(CompoundTag data, String key, HolderLookup.Provider registries) {
        buffer.clear();
        var list = data.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                buffer.queue(stack.what(), stack.amount());
            }
        }
    }

    public void write(CompoundTag data, String key, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : buffer.entries()) {
            list.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
        }
        data.put(key, list);
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (var entry : buffer.entries()) {
            entry.getKey().addDrops(entry.getValue(), drops, level, pos);
        }
    }

    public void clear() {
        buffer.clear();
    }
}
