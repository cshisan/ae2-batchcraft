package org.ae2bc.part.general;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.ae2bc.common.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** 一次接单内共享模拟占用，不修改实际库存。 */
public final class Pp2mpInsertPlan {
    private @Nullable Map<Object, Slots> slots;
    private @Nullable Map<MEStorage, Map<AEKey, Long>> generic;
    private @Nullable Map<IFluidHandler, Map<AEKey, Long>> fluids;

    /* 仅为本次接单实际使用的后端建立模拟状态。 */
    private Map<Object, Slots> slots() {
        if (this.slots == null) {
            this.slots = new IdentityHashMap<>();
        }
        return this.slots;
    }

    /* 同一个空槽不能被本次计划的两种材料重复占用。 */
    public long reserve(IItemHandler handler, AEItemKey key, long amount) {
        Slots plan = this.slots().computeIfAbsent(handler, ignored -> new Slots(handler.getSlots()));
        long remaining = amount;
        for (int slot = 0; slot < plan.keys.length && remaining > 0; slot++) {
            ItemStack current = handler.getStackInSlot(slot);
            if ((!current.isEmpty() && !key.matches(current)) || !plan.accepts(slot, key)) {
                continue;
            }
            int requested = (int) Math.min(Integer.MAX_VALUE, Utils.saturatingAdd(remaining, plan.amounts[slot]));
            ItemStack rejected = handler.insertItem(slot, key.toStack(requested), true);
            long available = Math.max(0, requested - rejected.getCount() - plan.amounts[slot]);
            long used = Math.min(remaining, available);
            plan.reserve(slot, key, used);
            remaining -= used;
        }
        return amount - remaining;
    }

    /* 同时遵守整组 fill 模拟和各槽过滤、容量；模拟不会锁定机器。 */
    public long reserve(IFluidHandler handler, AEFluidKey key, long amount) {
        if (this.fluids == null) {
            this.fluids = new IdentityHashMap<>();
        }
        Map<AEKey, Long> reserved = this.fluids.computeIfAbsent(handler, ignored -> new HashMap<>());
        long previous = reserved.getOrDefault(key, 0L);
        int requested = (int) Math.min(Integer.MAX_VALUE, Utils.saturatingAdd(previous, amount));
        long available = Math.max(0, handler.fill(key.toStack(requested), IFluidHandler.FluidAction.SIMULATE) - previous);
        long remaining = Math.min(amount, available);
        long accepted = remaining;
        Slots plan = this.slots().computeIfAbsent(handler, ignored -> new Slots(handler.getTanks()));
        for (int slot = 0; slot < plan.keys.length && remaining > 0; slot++) {
            FluidStack current = handler.getFluidInTank(slot);
            if ((!current.isEmpty() && !key.equals(AEFluidKey.of(current))) || !plan.accepts(slot, key)
                    || !handler.isFluidValid(slot, key.toStack(1))) {
                continue;
            }
            long capacity = Math.max(0L, (long) handler.getTankCapacity(slot) - current.getAmount() - plan.amounts[slot]);
            long used = Math.min(remaining, capacity);
            plan.reserve(slot, key, used);
            remaining -= used;
        }
        accepted -= remaining;
        reserved.put(key, previous + accepted);
        return accepted;
    }

    /* shortcut: 通用 AE 存储没有跨类型容量事务，实际插入差额仍由原任务保管。 */
    public long reserve(MEStorage storage, AEKey key, long amount, IActionSource source) {
        if (this.generic == null) {
            this.generic = new IdentityHashMap<>();
        }
        Map<AEKey, Long> reserved = this.generic.computeIfAbsent(storage, ignored -> new HashMap<>());
        long previous = reserved.getOrDefault(key, 0L);
        long requested = Utils.saturatingAdd(previous, amount);
        long accepted = Math.clamp(storage.insert(key, requested, Actionable.SIMULATE, source) - previous, 0, amount);
        reserved.put(key, previous + accepted);
        return accepted;
    }

    private static final class Slots {
        private final AEKey[] keys;
        private final long[] amounts;

        private Slots(int count) {
            this.keys = new AEKey[count];
            this.amounts = new long[count];
        }

        private boolean accepts(int slot, AEKey key) {
            return this.keys[slot] == null || this.keys[slot].equals(key);
        }

        private void reserve(int slot, AEKey key, long amount) {
            if (amount > 0) {
                this.keys[slot] = key;
                this.amounts[slot] += amount;
            }
        }
    }
}
