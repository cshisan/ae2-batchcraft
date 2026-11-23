package org.ae2bc.storage;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 无本地库存的插入适配器，资源由具体实现直接交给目标。
 */
@SuppressWarnings("UnstableApiUsage")
public abstract class InsertOnlyStorage implements GenericInternalInventory, MEStorage {
    @Override
    public Component getDescription() {
        return Component.empty();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public @Nullable GenericStack getStack(int slot) {
        return null;
    }

    @Override
    public @Nullable AEKey getKey(int slot) {
        return null;
    }

    @Override
    public long getAmount(int slot) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        return this.insert(key, amount, mode, null);
    }

    @Override
    public long extract(int slot, AEKey key, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void beginBatch() {
    }

    @Override
    public void endBatch() {
    }

    @Override
    public void endBatchSuppressed() {
    }

    @Override
    public void onChange() {
    }
}
