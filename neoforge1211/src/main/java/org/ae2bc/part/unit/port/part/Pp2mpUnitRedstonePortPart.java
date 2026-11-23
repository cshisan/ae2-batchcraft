package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import appeng.util.Platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.ae2bc.core.unit.RedstonePortParam;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

public class Pp2mpUnitRedstonePortPart extends Pp2mpUnitPortPart {
    private int redstonePower;
    private boolean worldStateDirty = true;

    public Pp2mpUnitRedstonePortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.REDSTONE, Pp2mpUnitPortPartModel.MODEL_REDSTONE);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.redstonePower = 0;
        this.worldStateDirty = true;
    }

    /* 根据管理器当前任务运行时间更新本端口的红石输出。 */
    public boolean updatePower(RedstonePortParam param, long activeTicks) {
        return this.updatePower(param == null ? 0 : param.powerAt(activeTicks));
    }

    /* 管理器不再持有活动任务时清除红石输出。 */
    public void resetPower() {
        this.updatePower(0);
    }

    public boolean updatePower(int power) {
        int clamped = Math.clamp(power, 0, 15);
        if (this.redstonePower == clamped && !this.worldStateDirty) {
            return false;
        }
        this.redstonePower = clamped;
        this.worldStateDirty = false;
        Level level = this.getLevel();
        if (level != null) {
            Platform.notifyBlocksOfNeighbors(level, this.getBlockEntity().getBlockPos());
        }
        return true;
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public int isProvidingStrongPower() {
        return this.redstonePower;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.redstonePower;
    }
}
