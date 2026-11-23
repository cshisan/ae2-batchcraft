package org.ae2bc.part.output;

import appeng.api.stacks.AEKey;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.common.Version;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.constant.Pp2mpOutputKeys;
import org.ae2bc.core.part.*;
import org.ae2bc.part.task.Pp2mpTaskData;
import org.ae2bc.part.codec.GeneralPageAttrCodec;
import org.ae2bc.part.codec.GeneralPageAttrPacket;
import org.ae2bc.part.codec.OutputPageAttrCodec;
import org.ae2bc.part.codec.OutputPageAttrPacket;

import java.util.LinkedHashMap;
import java.util.Map;

public class Pp2mpOutputData extends Pp2mpTaskData implements ConfigLocal<InputConfig> {
    private final Runnable markForSave;
    private final Runnable markForUpdate;
    private final Runnable wakeExtractor;
    // 空方向代表端口默认插入面，其他方向使用样板指定的机器面。
    private final @Getter Map<Direction, Map<AEKey, Long>> pendingInputs = new LinkedHashMap<>();
    private @Getter @Setter boolean configSyncSwitch = true;
    private @Getter OutputConfig config = OutputConfig.DEFAULT;

    public Pp2mpOutputData(Runnable markForSave, Runnable markForUpdate, Runnable wakeExtractor) {
        this.markForSave = markForSave;
        this.markForUpdate = markForUpdate;
        this.wakeExtractor = wakeExtractor;
    }

    public void update(OutputConfig config) {
        if (!this.configSyncSwitch && !Version.equals(config, this.config)) {
            this.update(() -> this.config = config);
        }
    }

    public void update(Runnable update) {
        update.run();
        this.markForSave.run();
        this.markForUpdate.run();
        this.wakeExtractor.run();
    }

    @Override
    public void apply(InputConfig inputConfig) {
        if (this.configSyncSwitch && inputConfig.version() != this.config.version()) {
            this.update(() -> this.config = new OutputConfig(inputConfig));
        }
    }

    /* 写入NBT */
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag generalData = new CompoundTag();
        GeneralPageAttrCodec.write(generalData, this.config.general());

        CompoundTag outputData = new CompoundTag();
        OutputPageAttrCodec.write(outputData, this.config.output());

        data.putBoolean(Pp2mpOutputKeys.OUTPUT_SYNCED_ENABLED, this.configSyncSwitch);
        data.putInt(Pp2mpOutputKeys.CONFIG_VERSION, this.config.version());
        data.put(GeneralPageAttrCodec.CONFIG_GENERAL, generalData);
        data.put(OutputPageAttrCodec.CONFIG_OUTPUT, outputData);
        CompoundTag task = this.writeTask(data, registries);
        ListTag pending = new ListTag();
        for (Map.Entry<Direction, Map<AEKey, Long>> entry : this.pendingInputs.entrySet()) {
            CompoundTag group = new CompoundTag();
            group.putInt(Pp2mpOutputKeys.PENDING_SIDE, entry.getKey() == null ? -1 : entry.getKey().ordinal());
            group.put(Pp2mpOutputKeys.PENDING_STACKS, MinecraftHelper.keyCountMap2listTag(entry.getValue(), registries));
            pending.add(group);
        }
        task.put(Pp2mpOutputKeys.PENDING_INPUTS, pending);
    }

    /* 从NBT读取 */
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        int version = data.getInt(Pp2mpOutputKeys.CONFIG_VERSION);
        CompoundTag general = GeneralPageAttrCodec.read(data);
        CompoundTag output = OutputPageAttrCodec.read(data);

        this.config = OutputConfig.restore(
                version, GeneralPageAttrCodec.returnMode(general),
                OutputPageAttrCodec.productExtraction(output),
                GeneralPageAttrCodec.extractionParam(general), GeneralPageAttrCodec.blocking(general)
        );
        this.configSyncSwitch = data.getBoolean(Pp2mpOutputKeys.OUTPUT_SYNCED_ENABLED);
        CompoundTag task = this.readTask(data, registries);
        this.pendingInputs.clear();
        ListTag pending = task.getList(Pp2mpOutputKeys.PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < pending.size(); index++) {
            CompoundTag group = pending.getCompound(index);
            int side = group.getInt(Pp2mpOutputKeys.PENDING_SIDE);
            Direction direction = side >= 0 && side < Direction.values().length ? Direction.values()[side] : null;
            Map<AEKey, Long> stacks = new LinkedHashMap<>();
            MinecraftHelper.listTag2keyCountMap(group, registries, Pp2mpOutputKeys.PENDING_STACKS, stacks);
            if (!stacks.isEmpty()) {
                this.pendingInputs.put(direction, stacks);
            }
        }
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeBoolean(this.configSyncSwitch);
        data.writeInt(this.config.version());
        GeneralPageAttrPacket.write(data, this.config.general());
        OutputPageAttrPacket.write(data, this.config.output());
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean oldSync = this.configSyncSwitch;
        this.configSyncSwitch = data.readBoolean();

        int oldVersion = this.config.version();
        int version = data.readInt();
        GeneralPageConfig general = GeneralPageAttrPacket.read(data);
        OutputPageConfig output = OutputPageAttrPacket.read(data);
        this.config = OutputConfig.restore(
                version, general.returnMode(),
                output.productExtraction(),
                general.extractionParam(), general.blocking()
        );

        return oldSync != this.configSyncSwitch || oldVersion != this.config.version();
    }
}
