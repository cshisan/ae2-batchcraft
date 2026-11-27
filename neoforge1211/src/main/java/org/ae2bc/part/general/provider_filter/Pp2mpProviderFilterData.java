package org.ae2bc.part.general.provider_filter;

import appeng.api.stacks.*;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.ae2bc.core.constant.Pp2mpProviderFilterKeys;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class Pp2mpProviderFilterData {
    public static final int SLOT_COUNT = 18;

    private final ConfigInventory markers;
    private final IUpgradeInventory upgrades;
    private final long[] limits = new long[SLOT_COUNT];
    private final Runnable changed;
    private boolean loading;
    private @Nullable Boolean copiedWhitelist;
    private @Nullable Pp2mpProviderFilterConfig snapshot;

    public Pp2mpProviderFilterData(ItemLike item, Runnable changed) {
        this.changed = changed;
        this.markers = ConfigInventory.configTypes(SLOT_COUNT)
                .supportedTypes(Set.copyOf(AEKeyTypes.getAll()))
                .changeListener(this::onChanged)
                .allowOverstacking(false)
                .build();
        this.upgrades = UpgradeInventories.forMachine(item, 1, this::onUpgradesChanged);
    }

    /* 复用供应端口已有的标记槽与升级槽，不创建第二份物品库存。 */
    public Pp2mpProviderFilterData(ConfigInventory markers, IUpgradeInventory upgrades, Runnable changed) {
        this.markers = markers;
        this.upgrades = upgrades;
        this.changed = changed;
    }

    /* 掉落本地真实升级卡，不掉落标记资源。 */
    public void addDrops(List<ItemStack> drops) {
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    /* 仅配置变化后构建一次，稳定状态复用不可变快照。 */
    public Pp2mpProviderFilterConfig snapshot() {
        if (this.snapshot == null) {
            List<Pp2mpProviderFilterConfig.Entry> entries = new ArrayList<>(SLOT_COUNT);
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                entries.add(new Pp2mpProviderFilterConfig.Entry(this.markers.getKey(slot), this.limits[slot]));
            }
            this.snapshot = new Pp2mpProviderFilterConfig(this.isWhitelist(), entries);
        }
        return this.snapshot;
    }

    /* 相同值的库存回调不重复保存和推送。 */
    public void onChanged() {
        if (!this.loading) {
            Pp2mpProviderFilterConfig previous = this.snapshot;
            this.snapshot = null;
            if (!this.snapshot().equals(previous)) {
                this.changed.run();
            }
        }
    }

    /* 玩家实际更换升级卡后，由本地升级卡重新决定黑白名单模式。 */
    public void onUpgradesChanged() {
        if (!this.loading) {
            this.copiedWhitelist = null;
            this.snapshot = null;
            this.changed.run();
        }
    }

    /* 来源重绑也比较完整规则，实际升级卡始终保留在本地。 */
    public void copyFrom(Pp2mpProviderFilterData source) {
        Pp2mpProviderFilterConfig incoming = source.snapshot();
        if (this.snapshot().equals(incoming)) {
            this.snapshot = incoming;
            return;
        }
        this.loading = true;
        try {
            for (int slot = 0; slot < this.markers.size(); slot++) {
                Pp2mpProviderFilterConfig.Entry entry = incoming.entries().get(slot);
                AEKey key = entry.key();
                if (!Objects.equals(this.markers.getKey(slot), key)) {
                    this.markers.setStack(slot, key == null ? null : new GenericStack(key, 0));
                }
                this.limits[slot] = entry.amount();
            }
            this.copiedWhitelist = incoming.whitelist();
        } finally {
            this.loading = false;
        }
        this.snapshot = incoming;
        this.changed.run();
    }

    public ConfigInventory markers() {
        return this.markers;
    }

    public IUpgradeInventory upgrades() {
        return this.upgrades;
    }

    public boolean allows(AEKey key) {
        boolean marked = this.slotOf(key) >= 0;
        if (this.markers.isEmpty()) {
            return true;
        }
        return this.isWhitelist() == marked;
    }

    public long targetAmount(AEKey key) {
        if (!this.isWhitelist()) {
            return 0;
        }
        int slot = this.slotOf(key);
        return slot < 0 ? 0 : this.limits[slot];
    }

    public long limit(int slot) {
        return slot >= 0 && slot < this.limits.length ? this.limits[slot] : 0;
    }

    public boolean isWhitelist() {
        return this.copiedWhitelist != null ? this.copiedWhitelist : !this.upgrades.isInstalled(AEItems.INVERTER_CARD);
    }

    /* 换资源时连同数量一起更新，只发布最终规则；重复标记保留原数量。 */
    public void setMarker(int slot, @Nullable GenericStack marker) {
        if (slot < 0 || slot >= this.markers.size()) {
            return;
        }
        AEKey previous = this.markers.getKey(slot);
        if (Objects.equals(previous, marker == null ? null : marker.what())) {
            return;
        }
        this.loading = true;
        try {
            this.markers.setStack(slot, marker);
            if (Objects.equals(previous, this.markers.getKey(slot))) {
                return;
            }
            this.limits[slot] = 0;
        } finally {
            this.loading = false;
        }
        this.onChanged();
    }

    /* 只有实际数量变化才触发保存和规则通知。 */
    public void setLimit(int slot, long amount) {
        if (slot < 0 || slot >= this.limits.length) {
            return;
        }
        long normalized = Math.max(0, amount);
        if (this.limits[slot] != normalized) {
            this.limits[slot] = normalized;
            this.onChanged();
        }
    }

    public long amountToProvider(AEKey key, long currentAmount, long available) {
        if (this.allows(key)) {
            long target = this.targetAmount(key);
            return target == 0 ? available : Math.clamp(target - currentAmount, 0, available);
        }
        return 0;
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.markers.writeToChildTag(tag, Pp2mpProviderFilterKeys.MARKERS, registries);
        this.upgrades.writeToNBT(tag, Pp2mpProviderFilterKeys.UPGRADES, registries);
        ListTag limitsTag = new ListTag();
        for (long limit : this.limits) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(Pp2mpProviderFilterKeys.LIMIT_VALUE, limit);
            limitsTag.add(entry);
        }
        tag.put(Pp2mpProviderFilterKeys.LIMITS, limitsTag);
        this.writeCopiedWhitelist(tag);
    }

    private void writeCopiedWhitelist(CompoundTag tag) {
        if (this.copiedWhitelist != null) {
            tag.putBoolean(Pp2mpProviderFilterKeys.COPIED_WHITELIST, this.copiedWhitelist);
        } else {
            tag.remove(Pp2mpProviderFilterKeys.COPIED_WHITELIST);
        }
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.loading = true;
        try {
            this.markers.readFromChildTag(tag, Pp2mpProviderFilterKeys.MARKERS, registries);
            this.upgrades.readFromNBT(tag, Pp2mpProviderFilterKeys.UPGRADES, registries);
            this.readLimits(tag);
        } finally {
            this.loading = false;
            this.snapshot = null;
        }
    }

    /* 保留供应端口原有的数量数组存档格式。 */
    public void writeLimits(CompoundTag tag) {
        tag.putLongArray(Pp2mpProviderFilterKeys.LIMITS, this.limits.clone());
        this.writeCopiedWhitelist(tag);
    }

    public void readLimits(CompoundTag tag) {
        Arrays.fill(this.limits, 0);
        if (tag.contains(Pp2mpProviderFilterKeys.LIMITS, Tag.TAG_LONG_ARRAY)) {
            long[] values = tag.getLongArray(Pp2mpProviderFilterKeys.LIMITS);
            for (int slot = 0; slot < Math.min(this.limits.length, values.length); slot++) {
                this.limits[slot] = Math.max(0, values[slot]);
            }
        } else {
            ListTag limitsTag = tag.getList(Pp2mpProviderFilterKeys.LIMITS, Tag.TAG_COMPOUND);
            for (int slot = 0; slot < Math.min(this.limits.length, limitsTag.size()); slot++) {
                this.limits[slot] = Math.max(0, limitsTag.getCompound(slot).getLong(Pp2mpProviderFilterKeys.LIMIT_VALUE));
            }
        }
        this.copiedWhitelist = tag.contains(Pp2mpProviderFilterKeys.COPIED_WHITELIST, Tag.TAG_BYTE)
                ? tag.getBoolean(Pp2mpProviderFilterKeys.COPIED_WHITELIST) : null;
        this.snapshot = null;
    }

    private int slotOf(AEKey key) {
        for (int slot = 0; slot < this.markers.size(); slot++) {
            if (key.equals(this.markers.getKey(slot))) {
                return slot;
            }
        }
        return -1;
    }
}
