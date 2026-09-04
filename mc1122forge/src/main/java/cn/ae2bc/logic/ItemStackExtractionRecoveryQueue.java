package cn.ae2bc.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Durable item-only recovery queue for the legacy extraction implementation. */
public final class ItemStackExtractionRecoveryQueue {
    private final Map<StackKey, Long> buffer = new LinkedHashMap<StackKey, Long>();
    private final Runnable changeListener;

    public ItemStackExtractionRecoveryQueue(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public void queue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        queueAmount(new StackKey(stack), stack.getCount());
        changeListener.run();
    }

    public boolean drain(final Function<ItemStack, ItemStack> inserter) {
        boolean changed = false;
        for (java.util.Iterator<Map.Entry<StackKey, Long>> iterator = buffer.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<StackKey, Long> entry = iterator.next();
            ItemStack offered = entry.getKey().toStack();
            offered.setCount((int) Math.min(Integer.MAX_VALUE, entry.getValue().longValue()));
            ItemStack remainder = inserter.apply(offered);
            long inserted;
            if (remainder == null || remainder.isEmpty()) {
                inserted = offered.getCount();
            } else if (!entry.getKey().matches(remainder)) {
                inserted = 0L;
            } else {
                inserted = Math.max(0, offered.getCount() - remainder.getCount());
            }
            inserted = Math.max(0L, Math.min(inserted, entry.getValue().longValue()));
            if (inserted >= entry.getValue().longValue()) {
                iterator.remove();
                changed = true;
            } else if (inserted > 0) {
                entry.setValue(entry.getValue().longValue() - inserted);
                changed = true;
            }
        }
        if (changed) {
            changeListener.run();
        }
        return changed;
    }

    public void read(NBTTagCompound data, String key) {
        buffer.clear();
        NBTTagList entries = data.getTagList(key, 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(entry.getCompoundTag("Stack"));
            long amount = entry.getLong("Amount");
            if (!stack.isEmpty() && amount > 0) {
                queueAmount(new StackKey(stack), amount);
            }
        }
    }

    public void write(NBTTagCompound data, String key) {
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<StackKey, Long> buffered : buffer.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("Stack", buffered.getKey().toStack().writeToNBT(new NBTTagCompound()));
            entry.setLong("Amount", buffered.getValue().longValue());
            entries.appendTag(entry);
        }
        data.setTag(key, entries);
    }

    public void addDrops(List<ItemStack> drops) {
        for (Map.Entry<StackKey, Long> buffered : buffer.entrySet()) {
            long remaining = buffered.getValue().longValue();
            ItemStack identity = buffered.getKey().toStack();
            int stackLimit = Math.max(1, identity.getMaxStackSize());
            while (remaining > 0) {
                ItemStack drop = identity.copy();
                int count = (int) Math.min((long) stackLimit, remaining);
                drop.setCount(count);
                drops.add(drop);
                remaining -= count;
            }
        }
    }

    public void clear() {
        buffer.clear();
    }

    private void queueAmount(StackKey key, long amount) {
        Long previous = buffer.get(key);
        buffer.put(key, previous == null ? amount : saturatingAdd(previous.longValue(), amount));
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final class StackKey {
        private final NBTTagCompound serialized;

        private StackKey(ItemStack stack) {
            ItemStack identity = stack.copy();
            identity.setCount(1);
            this.serialized = identity.writeToNBT(new NBTTagCompound());
        }

        private ItemStack toStack() {
            return new ItemStack(serialized.copy());
        }

        private boolean matches(ItemStack stack) {
            return equals(new StackKey(stack));
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof StackKey
                    && serialized.equals(((StackKey) other).serialized);
        }

        @Override
        public int hashCode() {
            return serialized.hashCode();
        }
    }
}
