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
import net.minecraft.network.RegistryFriendlyByteBuf;
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
    private int version;
    private int receivedVersion;
    private boolean streamInitialized;
    private boolean loading;
    private @Nullable Boolean copiedWhitelist;
    private @Nullable Pp2mpProviderFilterData copiedSource;
    private int copiedSourceVersion = Integer.MIN_VALUE;
    private int copiedLocalVersion = Integer.MIN_VALUE;

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

    public int version() {
        return this.version;
    }

    /* 标记配置变更，供菜单按版本同步。 */
    public void onChanged() {
        if (!this.loading) {
            this.version++;
            this.changed.run();
        }
    }

    /* 玩家实际更换升级卡后，由本地升级卡重新决定黑白名单模式。 */
    private void onUpgradesChanged() {
        if (!this.loading) {
            this.copiedWhitelist = null;
            this.onChanged();
        }
    }

    /* 按配置来源和版本覆盖本地规则，只复制配置，不复制实际升级卡。 */
    public void copyFrom(Pp2mpProviderFilterData source) {
        if (source == this || (this.copiedSource == source
                && this.copiedSourceVersion == source.version()
                && this.copiedLocalVersion == this.version)) {
            return;
        }
        this.loading = true;
        try {
            for (int slot = 0; slot < this.markers.size(); slot++) {
                AEKey key = source.markers().getKey(slot);
                this.markers.setStack(slot, key == null ? null : new GenericStack(key, 0));
                this.limits[slot] = key == null ? 0 : source.limit(slot);
            }
            this.copiedWhitelist = source.isWhitelist();
        } finally {
            this.loading = false;
        }
        this.onChanged();
        this.copiedSource = source;
        this.copiedSourceVersion = source.version();
        this.copiedLocalVersion = this.version;
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

    public boolean isMarked(int slot) {
        return slot >= 0 && slot < this.markers.size() && this.markers.getKey(slot) != null;
    }

    /* 接受有效数量编辑即推进配置版本，不逐字段判定是否需要更新。 */
    public void setLimit(int slot, long amount) {
        if (slot < 0 || slot >= this.limits.length) {
            return;
        }
        this.limits[slot] = Math.max(0, amount);
        this.onChanged();
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
            this.copiedWhitelist = tag.contains(Pp2mpProviderFilterKeys.COPIED_WHITELIST, Tag.TAG_BYTE)
                    ? tag.getBoolean(Pp2mpProviderFilterKeys.COPIED_WHITELIST) : null;
            this.copiedSource = null;
        } finally {
            this.loading = false;
            this.version++;
            this.streamInitialized = false;
        }
    }

    /* 保留供应端口原有的数量数组存档格式。 */
    public void writeLimits(CompoundTag tag) {
        tag.putLongArray(Pp2mpProviderFilterKeys.LIMITS, this.limits.clone());
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
        if (!this.loading) {
            this.version++;
            this.streamInitialized = false;
        }
    }

    /* 版本与数量快照一起发送，接收端不需要逐槽比较字段值。 */
    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeVarInt(this.version);
        for (long limit : this.limits) {
            data.writeVarLong(limit);
        }
    }

    /* 首包或版本变化时整组更新；重复版本仍消费全部字节，避免部件流错位。 */
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        int incomingVersion = data.readVarInt();
        boolean changed = !this.streamInitialized || incomingVersion != this.receivedVersion;
        for (int slot = 0; slot < this.limits.length; slot++) {
            long amount = Math.max(0, data.readVarLong());
            if (changed) {
                this.limits[slot] = amount;
            }
        }
        if (changed) {
            this.version = incomingVersion;
            this.receivedVersion = incomingVersion;
            this.streamInitialized = true;
        }
        return changed;
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
