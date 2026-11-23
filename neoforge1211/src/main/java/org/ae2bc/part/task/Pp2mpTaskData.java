package org.ae2bc.part.task;

import appeng.api.stacks.AEKey;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.constant.Pp2mpTaskKeys;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 每个端点只交付一笔材料，可同时保留多笔等待产物的账目。 */
@Getter
public abstract class Pp2mpTaskData {
    private final Map<UUID, Pp2mpTaskRecord> tasks = new LinkedHashMap<>();
    private final Set<UUID> completions = new LinkedHashSet<>();
    private final Pp2mpTaskReceipt batchReceipt = new Pp2mpTaskReceipt();
    private final Map<AEKey, Long> inputRecovery = new LinkedHashMap<>();
    private final Map<AEKey, Long> extractionRecovery = new LinkedHashMap<>();
    private @Setter boolean cancelRequested;
    private @Nullable UUID deliveryId;
    private @Nullable UUID lastTaskId;

    /* 新交付独立记账，先前任务继续等待自己的产物。 */
    public void begin(Map<AEKey, Long> outputs, @Nullable AEKey primary, UUID id, @Nullable UUID converter) {
        if (this.tasks.containsKey(id)) {
            throw new IllegalStateException("Duplicate endpoint task: " + id);
        }
        this.addRecord(new Pp2mpTaskRecord(id, outputs, primary, converter));
        this.deliveryId = id;
        this.cancelRequested = false;
    }

    public boolean isTaskActive() {
        return !this.tasks.isEmpty();
    }

    public void rememberCompletion(UUID id) {
        this.lastTaskId = id;
    }

    public boolean hasRecovery() {
        return !this.inputRecovery.isEmpty() || !this.extractionRecovery.isEmpty();
    }

    private void addRecord(Pp2mpTaskRecord record) {
        this.tasks.put(record.getId(), record);
        if (!record.pending()) {
            this.completions.add(record.getId());
        }
    }

    /* 完成记录由结算迭代器移除，端点恢复余量不随单笔完成丢弃。 */
    public void afterCompletion() {
        if (!this.tasks.containsKey(this.deliveryId)) {
            this.deliveryId = null;
        }
        if (this.tasks.isEmpty() && !this.hasRecovery()) {
            this.cancelRequested = false;
            this.completions.clear();
        }
    }

    protected final CompoundTag writeTask(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(Pp2mpTaskKeys.CANCEL, this.cancelRequested);
        if (this.lastTaskId != null) {
            tag.putUUID(Pp2mpTaskKeys.LAST, this.lastTaskId);
        }
        if (this.deliveryId != null) {
            tag.putUUID(Pp2mpTaskKeys.DELIVERY, this.deliveryId);
        }
        ListTag records = new ListTag();
        for (Pp2mpTaskRecord record : this.tasks.values()) {
            records.add(record.write(registries));
        }
        tag.put(Pp2mpTaskKeys.RECORDS, records);
        tag.put(Pp2mpTaskKeys.INPUT_RECOVERY, MinecraftHelper.keyCountMap2listTag(this.inputRecovery, registries));
        tag.put(Pp2mpTaskKeys.EXTRACTION_RECOVERY, MinecraftHelper.keyCountMap2listTag(this.extractionRecovery, registries));
        this.batchReceipt.writeToNBT(tag);
        root.put(Pp2mpTaskKeys.TASK, tag);
        return tag;
    }

    protected final CompoundTag readTask(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag tag = root.getCompound(Pp2mpTaskKeys.TASK);
        this.tasks.clear();
        this.completions.clear();
        this.cancelRequested = tag.getBoolean(Pp2mpTaskKeys.CANCEL);
        this.lastTaskId = tag.hasUUID(Pp2mpTaskKeys.LAST) ? tag.getUUID(Pp2mpTaskKeys.LAST) : null;
        this.deliveryId = tag.hasUUID(Pp2mpTaskKeys.DELIVERY) ? tag.getUUID(Pp2mpTaskKeys.DELIVERY) : null;
        if (tag.contains(Pp2mpTaskKeys.RECORDS, Tag.TAG_LIST)) {
            ListTag records = tag.getList(Pp2mpTaskKeys.RECORDS, Tag.TAG_COMPOUND);
            for (int index = 0; index < records.size(); index++) {
                Pp2mpTaskRecord record = Pp2mpTaskRecord.read(records.getCompound(index), registries);
                if (record != null) {
                    this.addRecord(record);
                }
            }
        } else if (tag.getBoolean(Pp2mpTaskKeys.ACTIVE)) {
            // 旧单任务原样作为一笔记录读取，保留材料与票据归属。
            Pp2mpTaskRecord record = Pp2mpTaskRecord.read(tag, registries);
            if (record != null) {
                this.addRecord(record);
                this.deliveryId = record.getId();
            }
        }
        MinecraftHelper.listTag2keyCountMap(tag, registries, Pp2mpTaskKeys.INPUT_RECOVERY, this.inputRecovery);
        MinecraftHelper.listTag2keyCountMap(tag, registries, Pp2mpTaskKeys.EXTRACTION_RECOVERY, this.extractionRecovery);
        if (this.cancelRequested) {
            this.completions.addAll(this.tasks.keySet());
        }
        this.batchReceipt.readFromNBT(tag);
        return tag;
    }
}
