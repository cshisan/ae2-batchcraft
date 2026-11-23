package org.ae2bc.part.unit.manager;

import appeng.api.stacks.GenericStack;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ItemLike;
import org.ae2bc.common.Version;
import org.ae2bc.core.constant.Pp2mpUnitManagerKeys;
import org.ae2bc.core.part.*;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.part.task.Pp2mpTaskData;
import org.ae2bc.part.codec.GeneralPageAttrCodec;
import org.ae2bc.part.codec.GeneralPageAttrPacket;
import org.ae2bc.part.codec.UnitPageAttrCodec;
import org.ae2bc.part.codec.UnitPageAttrPacket;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.storage.PendingMaterial;

import java.util.*;

public class Pp2mpUnitManagerData extends Pp2mpTaskData implements ConfigCenter<UnitConfig>, ConfigLocal<InputConfig> {
    private final ConfigSource<UnitConfig> eventSource = new ConfigSource<>();
    private final Runnable markForSave;
    private final Runnable markForUpdate;
    private final @Getter Pp2mpProviderFilterData providerFilter;
    private @Getter
    final List<PendingMaterial> plannedSend = new ArrayList<>();
    private @Getter
    @Setter boolean syncEnabled = true;
    private @Getter UnitConfig config = UnitConfig.DEFAULT;
    private final @Getter Map<String, Pp2mpPortAssignment> portAssignments = new LinkedHashMap<>();

    public Pp2mpUnitManagerData(ItemLike item, Runnable markForSave, Runnable markForUpdate) {
        this.markForSave = markForSave;
        this.markForUpdate = markForUpdate;
        this.providerFilter = new Pp2mpProviderFilterData(item, this::onProviderFilterChanged);
    }

    /* 保存管理器本地供应规则，端口仅在开启同步时采用。 */
    private void onProviderFilterChanged() {
        this.markForSave.run();
        this.markForUpdate.run();
    }

    public ConfigSource<UnitConfig> eventSource() {
        return eventSource;
    }

    public void update(UnitConfig config) {
        if (this.syncEnabled) {
            return;
        }
        if (!Version.equals(config, this.config)) {
            this.update(() -> this.config = config);
            this.push();
        }
    }

    public void update(Runnable update) {
        update.run();
        this.markForSave.run();
        this.markForUpdate.run();
    }

    @Override
    public void push() {
        eventSource.firePush(config);
    }

    @Override
    public void apply(InputConfig inputConfig) {
        if (this.syncEnabled && inputConfig.version() != this.config.version()) {
            this.update(() -> this.config = new UnitConfig(inputConfig));
            this.push();
        }
    }

    /* 写入NBT */
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag generalData = new CompoundTag();
        GeneralPageAttrCodec.write(generalData, this.config.general());

        CompoundTag unitData = new CompoundTag();
        UnitPageAttrCodec.write(unitData, this.config.unit());

        data.putBoolean(Pp2mpUnitManagerKeys.UNIT_MANAGER_SYNCED_ENABLED, this.syncEnabled);
        data.putInt(Pp2mpUnitManagerKeys.CONFIG_VERSION, this.config.version());
        CompoundTag provider = new CompoundTag();
        this.providerFilter.writeToNBT(provider, registries);
        data.put(Pp2mpUnitManagerKeys.PROVIDER_FILTER, provider);
        data.put(GeneralPageAttrCodec.CONFIG_GENERAL, generalData);
        data.put(UnitPageAttrCodec.CONFIG_UNIT, unitData);
    }

    /* 从NBT读取 */
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        this.providerFilter.readFromNBT(data.getCompound(Pp2mpUnitManagerKeys.PROVIDER_FILTER), registries);
        int version = data.getInt(Pp2mpUnitManagerKeys.CONFIG_VERSION);
        CompoundTag general = GeneralPageAttrCodec.read(data);
        CompoundTag unit = UnitPageAttrCodec.read(data);

        this.syncEnabled = data.getBoolean(Pp2mpUnitManagerKeys.UNIT_MANAGER_SYNCED_ENABLED);
        this.config = UnitConfig.restore(
                version, GeneralPageAttrCodec.returnMode(general),
                UnitPageAttrCodec.breakPickup(unit),
                UnitPageAttrCodec.outputPortMode(unit),
                UnitPageAttrCodec.redstonePortParam(unit),
                GeneralPageAttrCodec.extractionParam(general), GeneralPageAttrCodec.blocking(general)
        );
    }

    public void writeTaskState(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag task = this.writeTask(data, registries);
        CompoundTag ports = new CompoundTag();
        this.portAssignments.forEach((id, assignment) -> ports.put(id, assignment.write(registries)));
        task.put(Pp2mpUnitManagerKeys.PORT_ASSIGNMENTS, ports);
        ListTag pending = new ListTag();
        for (PendingMaterial material : this.plannedSend) {
            CompoundTag entry = new CompoundTag();
            entry.put(Pp2mpUnitManagerKeys.PENDING_INPUTS_STACK, GenericStack.writeTag(registries, material.stack()));
            entry.putInt(Pp2mpUnitManagerKeys.PENDING_INPUTS_MODE, material.mode().id());
            entry.putInt(Pp2mpUnitManagerKeys.PENDING_INPUTS_SLOT, material.slot());
            pending.add(entry);
        }

        task.put(Pp2mpUnitManagerKeys.PENDING_INPUTS, pending);
    }

    public void readTaskState(CompoundTag data, HolderLookup.Provider registries) {
        CompoundTag task = this.readTask(data, registries);
        this.portAssignments.clear();
        this.plannedSend.clear();
        ListTag listTag = task.getList(Pp2mpUnitManagerKeys.PENDING_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            GenericStack stack = GenericStack.readTag(registries, entry.getCompound(Pp2mpUnitManagerKeys.PENDING_INPUTS_STACK));
            int mode = entry.getInt(Pp2mpUnitManagerKeys.PENDING_INPUTS_MODE);
            int slot = entry.getInt(Pp2mpUnitManagerKeys.PENDING_INPUTS_SLOT);
            if (stack != null && stack.amount() > 0) {
                this.plannedSend.add(new PendingMaterial(stack, MaterialOutputMode.fromId(mode), slot));
            }
        }
        if (!this.plannedSend.isEmpty()) {
            CompoundTag ports = task.getCompound(Pp2mpUnitManagerKeys.PORT_ASSIGNMENTS);
            for (String id : ports.getAllKeys()) {
                this.portAssignments.put(id, Pp2mpPortAssignment.read(ports.getCompound(id), registries));
            }
        }
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeBoolean(this.syncEnabled);
        data.writeInt(this.config.version());
        GeneralPageAttrPacket.write(data, this.config.general());
        UnitPageAttrPacket.write(data, this.config.unit());
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean oldSync = this.syncEnabled;
        this.syncEnabled = data.readBoolean();

        int oldVersion = this.config.version();
        int version = data.readInt();
        GeneralPageConfig general = GeneralPageAttrPacket.read(data);
        UnitPageConfig unit = UnitPageAttrPacket.read(data);
        this.config = UnitConfig.restore(
                version,
                general.returnMode(),
                unit.breakPickup(),
                unit.outputPortMode(),
                unit.redstonePortParam(),
                general.extractionParam(), general.blocking()
        );

        return oldSync != this.syncEnabled || oldVersion != this.config.version();
    }
}
