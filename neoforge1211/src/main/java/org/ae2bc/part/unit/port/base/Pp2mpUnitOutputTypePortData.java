package org.ae2bc.part.unit.port.base;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.ae2bc.core.constant.Pp2mpUnitPortKeys;
import org.ae2bc.core.part.ConfigLocal;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.unit.OutputPortMode;

public class Pp2mpUnitOutputTypePortData extends Pp2mpUnitPortData implements ConfigLocal<UnitConfig> {
    private @Getter
    @Setter boolean syncEnabled = true;
    private @Getter
    @Setter int priority = 0;
    private @Getter
    @Setter OutputPortMode outputPortMode = OutputPortMode.NORMAL;

    public Pp2mpUnitOutputTypePortData(Runnable markForSave, Runnable markForUpdate) {
        super(markForSave, markForUpdate);
    }

    @Override
    public void apply(UnitConfig config) {
        OutputPortMode mode = config.unit().outputPortMode();
        if (this.syncEnabled && this.outputPortMode != mode) {
            this.update(() -> this.outputPortMode = mode);
        }
    }

    public void writeToNBT(CompoundTag data) {
        data.putBoolean(Pp2mpUnitPortKeys.UNIT_PORT_SYNCED_ENABLED, this.syncEnabled);
        data.putInt(Pp2mpUnitPortKeys.PRIORITY, this.priority);
        data.putInt(Pp2mpUnitPortKeys.OUTPUT_PORT_MODE, this.outputPortMode.id);
    }

    public void readFromNBT(CompoundTag data) {
        this.syncEnabled = data.getBoolean(Pp2mpUnitPortKeys.UNIT_PORT_SYNCED_ENABLED);
        this.priority = data.getInt(Pp2mpUnitPortKeys.PRIORITY);
        this.outputPortMode = OutputPortMode.null2defaultOf(data.getInt(Pp2mpUnitPortKeys.OUTPUT_PORT_MODE));
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeBoolean(this.syncEnabled);
        data.writeInt(this.priority);
        data.writeVarInt(this.outputPortMode.id);
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean oldSyncSwitch = this.syncEnabled;
        this.syncEnabled = data.readBoolean();

        int oldPriority = this.priority;
        this.priority = data.readInt();

        OutputPortMode oldMode = this.outputPortMode;
        this.outputPortMode = OutputPortMode.null2defaultOf(data.readVarInt());

        return oldSyncSwitch != this.syncEnabled || oldPriority != this.priority || oldMode != this.outputPortMode;
    }
}
