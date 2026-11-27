package org.ae2bc.part.input;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.constant.Pp2mpInputKeys;
import org.ae2bc.core.constant.Pp2mpTaskKeys;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.part.*;
import org.ae2bc.part.codec.*;
import org.ae2bc.pattern.Pp2mpTaskBatch;
import org.ae2bc.pattern.Pp2mpTaskPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class Pp2mpInputData implements ConfigCenter<InputConfig> {
    private final @Getter(AccessLevel.NONE) ConfigSource<InputConfig> eventSource = new ConfigSource<>();
    private final @Getter(AccessLevel.NONE) Runnable markForSave;
    private final @Getter(AccessLevel.NONE) Runnable markForUpdate;
    private final @Getter(AccessLevel.NONE) AppEngInternalInventory legacyProviderUpgrades = new AppEngInternalInventory(1);
    private final Map<AEKey, Long> inputRecovery = new LinkedHashMap<>();
    private InputConfig config = InputConfig.DEFAULT;
    private @Setter int roundRobinCursor;
    private AEItemKey batchPattern;
    private KeyCounter[] batchInputs;
    private @Setter long batchTotalUnits;
    private @Setter long batchRemainingUnits;
    private @Setter long batchCursor;
    private UUID batchRootId;
    private String batchInFlightEndpointId;
    private long batchInFlightUnits;
    private long batchInFlightCursor;
    private boolean batchInFlightCommitted;
    private @Setter boolean batchCancelRequested;

    public Pp2mpInputData(Runnable markForSave, Runnable markForUpdate) {
        this.markForSave = markForSave;
        this.markForUpdate = markForUpdate;
    }

    /* 输入端不再持有供应规则，仅保留旧实物卡供拆除时掉落，避免入口迁移吞卡。 */
    public void addLegacyProviderUpgradeDrops(List<ItemStack> drops) {
        for (ItemStack stack : this.legacyProviderUpgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    public void clearLegacyProviderUpgrades() {
        this.legacyProviderUpgrades.clear();
    }

    public ConfigSource<InputConfig> eventSource() {
        return this.eventSource;
    }

    public void update(InputConfig config) {
        if (config != null && !this.config.equals(config)) {
            this.config = config;
            this.markForSave.run();
            this.markForUpdate.run();
            this.push();
        }
    }

    @Override
    public void push() {
        this.eventSource.firePush(this.config);
    }

    /* 写入NBT */
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag generalData = new CompoundTag();
        GeneralPageAttrCodec.write(generalData, this.config.general());

        CompoundTag outputData = new CompoundTag();
        OutputPageAttrCodec.write(outputData, this.config.output());

        CompoundTag unitData = new CompoundTag();
        UnitPageAttrCodec.write(unitData, this.config.unit());

        data.put(GeneralPageAttrCodec.CONFIG_GENERAL, generalData);
        data.put(OutputPageAttrCodec.CONFIG_OUTPUT, outputData);
        data.put(UnitPageAttrCodec.CONFIG_UNIT, unitData);
        data.putInt(Pp2mpInputKeys.ROUND_ROBIN_CURSOR, this.roundRobinCursor);
        data.putInt(Pp2mpInputKeys.TASK_ALLOCATION_MODE, this.config.taskAllocationMode().id);
        data.putInt(Pp2mpInputKeys.TASK_SPLIT_MODE, this.config.taskSplitMode().id);
        CompoundTag legacyProvider = new CompoundTag();
        this.legacyProviderUpgrades.writeToNBT(legacyProvider, Pp2mpInputKeys.LEGACY_PROVIDER_UPGRADES, registries);
        if (legacyProvider.isEmpty()) {
            data.remove(Pp2mpInputKeys.LEGACY_PROVIDER_FILTER);
        } else {
            data.put(Pp2mpInputKeys.LEGACY_PROVIDER_FILTER, legacyProvider);
        }
        CompoundTag task = new CompoundTag();
        this.writeBatchTask(task, registries);
        task.put(Pp2mpInputKeys.INPUT_RECOVERY,
                MinecraftHelper.keyCountMap2listTag(this.inputRecovery, registries));
        data.put(Pp2mpTaskKeys.TASK, task);
    }

    /* 从NBT读取 */
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag general = GeneralPageAttrCodec.read(data);
        CompoundTag output = OutputPageAttrCodec.read(data);
        CompoundTag unit = UnitPageAttrCodec.read(data);

        this.config = InputConfig.restore(
                GeneralPageAttrCodec.returnMode(general),
                OutputPageAttrCodec.productExtraction(output),
                UnitPageAttrCodec.breakPickup(unit),
                UnitPageAttrCodec.outputPortMode(unit),
                UnitPageAttrCodec.redstonePortParam(unit),
                GeneralPageAttrCodec.extractionParam(general),
                TaskAllocationMode.null2defaultOf(data.getInt(Pp2mpInputKeys.TASK_ALLOCATION_MODE)),
                TaskSplitMode.null2defaultOf(data.getInt(Pp2mpInputKeys.TASK_SPLIT_MODE)),
                GeneralPageAttrCodec.blocking(general)
        );
        this.roundRobinCursor = data.getInt(Pp2mpInputKeys.ROUND_ROBIN_CURSOR);
        this.legacyProviderUpgrades.clear();
        this.legacyProviderUpgrades.readFromNBT(
                data.getCompound(Pp2mpInputKeys.LEGACY_PROVIDER_FILTER),
                Pp2mpInputKeys.LEGACY_PROVIDER_UPGRADES, registries
        );
        CompoundTag task = data.getCompound(Pp2mpTaskKeys.TASK);
        this.readBatchTask(task, registries);
        MinecraftHelper.listTag2keyCountMap(task, registries,
                Pp2mpInputKeys.INPUT_RECOVERY, this.inputRecovery);
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeVarInt(this.config.taskAllocationMode().id);
        data.writeVarInt(this.config.taskSplitMode().id);
        GeneralPageAttrPacket.write(data, this.config.general());
        OutputPageAttrPacket.write(data, this.config.output());
        UnitPageAttrPacket.write(data, this.config.unit());
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        InputConfig previous = this.config;

        TaskAllocationMode taskAllocationMode = TaskAllocationMode.null2defaultOf(data.readVarInt());
        TaskSplitMode taskSplitMode = TaskSplitMode.null2defaultOf(data.readVarInt());
        GeneralPageConfig general = GeneralPageAttrPacket.read(data);
        OutputPageConfig output = OutputPageAttrPacket.read(data);
        UnitPageConfig unit = UnitPageAttrPacket.read(data);

        this.config = InputConfig.restore(
                general.returnMode(),
                output.productExtraction(),
                unit.breakPickup(),
                unit.outputPortMode(),
                unit.redstonePortParam(),
                general.extractionParam(),
                taskAllocationMode,
                taskSplitMode, general.blocking()
        );

        return !previous.equals(this.config);
    }

    public boolean hasBatchTask() {
        return this.batchPattern != null
                && this.batchInputs != null
                && this.batchTotalUnits > 0
                && (this.batchRemainingUnits > 0 || this.batchInFlightEndpointId != null)
                && this.batchRootId != null;
    }

    public void setBatchTask(AEItemKey pattern, KeyCounter[] inputs, long totalUnits, UUID rootId) {
        this.clearBatchInFlight();
        this.batchPattern = pattern;
        this.batchInputs = inputs;
        this.batchTotalUnits = totalUnits;
        this.batchRemainingUnits = totalUnits;
        this.batchCursor = 0;
        this.batchRootId = rootId;
        this.batchCancelRequested = false;
    }

    public void clearBatchTask() {
        this.batchPattern = null;
        this.batchInputs = null;
        this.batchTotalUnits = 0;
        this.batchRemainingUnits = 0;
        this.batchCursor = 0;
        this.batchRootId = null;
        this.batchCancelRequested = false;
        this.clearBatchInFlight();
    }

    public void setBatchInFlight(String endpointId, long units, long cursor) {
        this.batchInFlightEndpointId = endpointId;
        this.batchInFlightUnits = units;
        this.batchInFlightCursor = cursor;
        this.batchInFlightCommitted = false;
    }

    public void clearBatchInFlight() {
        this.batchInFlightEndpointId = null;
        this.batchInFlightUnits = 0;
        this.batchInFlightCursor = 0;
        this.batchInFlightCommitted = false;
    }

    /* 接收方已确认后才扣减待分发实物，提交标记保留到端点回执释放。 */
    public void commitBatchInputs(KeyCounter[] handedOff) {
        if (this.batchInFlightCommitted) {
            return;
        }
        for (int slot = 0; slot < handedOff.length; slot++) {
            if (handedOff[slot] != null) {
                for (Object2LongMap.Entry<AEKey> entry : handedOff[slot]) {
                    if (this.batchInputs[slot].get(entry.getKey()) < entry.getLongValue()) {
                        throw new IllegalStateException("Batch input ownership mismatch");
                    }
                }
            }
        }
        for (int slot = 0; slot < handedOff.length; slot++) {
            if (handedOff[slot] != null) {
                this.batchInputs[slot].removeAll(handedOff[slot]);
                this.batchInputs[slot].removeZeros();
            }
        }
        this.commitBatchCounters();
    }

    public void commitBatchInputs(Pp2mpTaskPlan.InputTemplate template) {
        if (!this.batchInFlightCommitted) {
            template.deduct(this.batchInputs);
            this.commitBatchCounters();
        }
    }

    private void commitBatchCounters() {
        this.batchRemainingUnits -= this.batchInFlightUnits;
        this.batchCursor += this.batchInFlightUnits;
        this.batchInFlightCommitted = true;
    }

    /* 拆除时只掉落本地持有资源，排除已交付或归属尚未确认的在途批次。 */
    public Map<AEKey, Long> storedInputs(boolean excludeInFlight) {
        Map<AEKey, Long> stored = new LinkedHashMap<>(this.inputRecovery);
        if (!this.hasBatchTask()) {
            return stored;
        }
        Map<AEKey, Long> remaining = Pp2mpTaskPlan.amounts(this.batchInputs);
        if (excludeInFlight && this.batchInFlightEndpointId != null && !this.batchInFlightCommitted) {
            KeyCounter[] inFlight = Pp2mpTaskBatch.scaleInputs(
                    this.batchInputs, this.batchInFlightUnits, this.batchRemainingUnits, 0);
            Pp2mpTaskPlan.amounts(inFlight).forEach(
                    (key, amount) -> remaining.computeIfPresent(key, (ignored, held) -> held - amount));
        }
        remaining.forEach((key, amount) -> {
            if (amount > 0) {
                stored.merge(key, amount, Math::addExact);
            }
        });
        return stored;
    }

    /* 只取消输入端仍持有的资源；未确定归属的交付必须先完成确认。 */
    public void recoverBatchInputs() {
        if (this.batchInFlightEndpointId != null) {
            throw new IllegalStateException("Cannot cancel an unresolved batch delivery");
        }
        if (this.hasBatchTask()) {
            Map<AEKey, Long> recovered = Pp2mpTaskPlan.amounts(this.batchInputs);
            recovered.replaceAll((key, amount) -> Math.addExact(
                    this.inputRecovery.getOrDefault(key, 0L), amount
            ));
            this.inputRecovery.putAll(recovered);
        }
        this.clearBatchTask();
    }

    private void writeBatchTask(CompoundTag data, HolderLookup.Provider registries) {
        if (!this.hasBatchTask()) {
            data.remove(Pp2mpInputKeys.BATCH_TASK_PATTERN);
            data.remove(Pp2mpInputKeys.BATCH_TASK_INPUTS);
            data.remove(Pp2mpInputKeys.BATCH_IN_FLIGHT_ENDPOINT);
            return;
        }
        data.put(Pp2mpInputKeys.BATCH_TASK_PATTERN, GenericStack.writeTag(registries, new GenericStack(this.batchPattern, 1)));
        ListTag inputList = new ListTag();
        for (int slot = 0; slot < this.batchInputs.length; slot++) {
            KeyCounter counter = this.batchInputs[slot];
            if (counter == null) {
                continue;
            }
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                if (entry.getLongValue() <= 0) {
                    continue;
                }
                CompoundTag input = GenericStack.writeTag(
                        registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())
                );
                input.putInt(Pp2mpInputKeys.BATCH_TASK_SLOT, slot);
                inputList.add(input);
            }
        }
        data.put(Pp2mpInputKeys.BATCH_TASK_INPUTS, inputList);
        data.putInt(Pp2mpInputKeys.BATCH_INPUT_SLOTS, this.batchInputs.length);
        data.putBoolean(Pp2mpInputKeys.BATCH_IN_FLIGHT_COMMITTED, this.batchInFlightCommitted);
        data.putBoolean(Pp2mpInputKeys.BATCH_CANCEL_REQUESTED, this.batchCancelRequested);
        data.putLong(Pp2mpInputKeys.BATCH_TASK_TOTAL_UNITS, this.batchTotalUnits);
        data.putLong(Pp2mpInputKeys.BATCH_TASK_REMAINING_UNITS, this.batchRemainingUnits);
        data.putLong(Pp2mpInputKeys.BATCH_TASK_CURSOR, this.batchCursor);
        data.putLong(Pp2mpInputKeys.BATCH_TASK_ROOT_MOST, this.batchRootId.getMostSignificantBits());
        data.putLong(Pp2mpInputKeys.BATCH_TASK_ROOT_LEAST, this.batchRootId.getLeastSignificantBits());
        if (this.batchInFlightEndpointId != null) {
            data.putString(Pp2mpInputKeys.BATCH_IN_FLIGHT_ENDPOINT, this.batchInFlightEndpointId);
            data.putLong(Pp2mpInputKeys.BATCH_IN_FLIGHT_UNITS, this.batchInFlightUnits);
            data.putLong(Pp2mpInputKeys.BATCH_IN_FLIGHT_CURSOR, this.batchInFlightCursor);
        } else {
            data.remove(Pp2mpInputKeys.BATCH_IN_FLIGHT_ENDPOINT);
        }
    }

    private void readBatchTask(CompoundTag data, HolderLookup.Provider registries) {
        this.clearBatchTask();
        if (!data.contains(Pp2mpInputKeys.BATCH_TASK_PATTERN, Tag.TAG_COMPOUND)) {
            return;
        }
        GenericStack patternStack = GenericStack.readTag(registries, data.getCompound(Pp2mpInputKeys.BATCH_TASK_PATTERN));
        if (patternStack == null || !(patternStack.what() instanceof AEItemKey itemKey)) {
            return;
        }
        ListTag inputList = data.getList(Pp2mpInputKeys.BATCH_TASK_INPUTS, Tag.TAG_COMPOUND);
        int slotCount = data.getInt(Pp2mpInputKeys.BATCH_INPUT_SLOTS);
        if (slotCount <= 0 || slotCount > 81) {
            return;
        }
        KeyCounter[] inputs = new KeyCounter[slotCount];
        for (int slot = 0; slot < inputs.length; slot++) {
            inputs[slot] = new KeyCounter();
        }
        for (int index = 0; index < inputList.size(); index++) {
            CompoundTag input = inputList.getCompound(index);
            GenericStack stack = GenericStack.readTag(registries, input);
            int slot = input.getInt(Pp2mpInputKeys.BATCH_TASK_SLOT);
            if (stack == null || stack.amount() <= 0 || slot < 0 || slot >= inputs.length) {
                continue;
            }
            inputs[slot].add(stack.what(), stack.amount());
        }
        this.batchPattern = itemKey;
        this.batchInputs = inputs;
        this.batchInFlightCommitted = data.getBoolean(Pp2mpInputKeys.BATCH_IN_FLIGHT_COMMITTED);
        this.batchCancelRequested = data.getBoolean(Pp2mpInputKeys.BATCH_CANCEL_REQUESTED);
        this.batchTotalUnits = data.getLong(Pp2mpInputKeys.BATCH_TASK_TOTAL_UNITS);
        this.batchRemainingUnits = data.getLong(Pp2mpInputKeys.BATCH_TASK_REMAINING_UNITS);
        this.batchCursor = data.getLong(Pp2mpInputKeys.BATCH_TASK_CURSOR);
        this.batchRootId = new UUID(
                data.getLong(Pp2mpInputKeys.BATCH_TASK_ROOT_MOST),
                data.getLong(Pp2mpInputKeys.BATCH_TASK_ROOT_LEAST)
        );
        if (data.contains(Pp2mpInputKeys.BATCH_IN_FLIGHT_ENDPOINT, Tag.TAG_STRING)) {
            this.batchInFlightEndpointId = data.getString(Pp2mpInputKeys.BATCH_IN_FLIGHT_ENDPOINT);
            this.batchInFlightUnits = data.getLong(Pp2mpInputKeys.BATCH_IN_FLIGHT_UNITS);
            this.batchInFlightCursor = data.getLong(Pp2mpInputKeys.BATCH_IN_FLIGHT_CURSOR);
        }
        if (!this.hasBatchTask()) {
            this.clearBatchTask();
        }
    }
}
