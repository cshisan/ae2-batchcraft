package org.ae2bc.part.unit.port.base;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import org.ae2bc.core.constant.Pp2mpUnitPortKeys;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpGridService;

import java.util.List;
import java.util.Set;

@Getter
public abstract class Pp2mpUnitIoTypePortPart extends Pp2mpUnitPortPart implements IUpgradeableObject {
    protected final ConfigInventory filterMarkers;
    private final IUpgradeInventory upgrades;

    public Pp2mpUnitIoTypePortPart(IPartItem<?> partItem, UnitPortType type, Pp2mpUnitPortPartModel models) {
        super(partItem, type, models);

        // 标记槽、升级槽
        this.filterMarkers = ConfigInventory.configTypes(18)
                .supportedTypes(Set.copyOf(AEKeyTypes.getAll()))
                .changeListener(this::onFilterChanged)
                .allowOverstacking(false)
                .build();
        this.upgrades = UpgradeInventories.forMachine(partItem.asItem(), 1, this::onUpgradesChanged);
    }

    /**
     * 返回本端口当前过滤模式是否接受指定的标记资源。
     */
    protected boolean allowsFilter(AEKey what) {
        boolean marked = false;
        for (int i = 0; i < this.filterMarkers.size(); i++) {
            AEKey marker = this.filterMarkers.getKey(i);
            if (what.equals(marker)) {
                marked = true;
                break;
            }
        }
        boolean inverted = this.upgrades.isInstalled(AEItems.INVERTER_CARD);
        return this.filterMarkers.isEmpty() || (inverted != marked);
    }

    public void upgradesChanged() {
        this.onFilterChanged();
    }

    private void onUpgradesChanged() {
        this.upgradesChanged();
    }

    protected void onFilterChanged() {
        this.getHost().markForSave();
        this.getHost().markForUpdate();
        this.alertManager();
    }

    protected void onAdjacentTargetChanged() {
        this.alertManager();
    }

    protected void clearAdjacentTarget() {
    }

    @Override
    public void removeFromWorld() {
        this.clearAdjacentTarget();
        super.removeFromWorld();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        this.onAdjacentTargetChanged();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.onAdjacentTargetChanged();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);

        this.filterMarkers.writeToChildTag(data, Pp2mpUnitPortKeys.FILTER_MARKERS, registries);
        this.upgrades.writeToNBT(data, Pp2mpUnitPortKeys.UPGRADES, registries);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);

        this.filterMarkers.readFromChildTag(data, Pp2mpUnitPortKeys.FILTER_MARKERS, registries);
        this.upgrades.readFromNBT(data, Pp2mpUnitPortKeys.UPGRADES, registries);

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean adventureMode) {
        super.addAdditionalDrops(drops, adventureMode);
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }
}
