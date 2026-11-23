package org.ae2bc.part.unit.port.part;

import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpProviderTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterHost;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitIoTypePortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;

import java.util.function.Consumer;

public final class Pp2mpUnitProviderPortPart extends Pp2mpUnitIoTypePortPart
        implements Pp2mpProviderFilterHost {
    private static final String PROVIDER_SYNC = "ProviderSync";

    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Pp2mpSendTarget sendTarget;
    private final Pp2mpProviderTarget providerTarget;
    private final @Getter Pp2mpProviderFilterData providerFilter;
    private @Getter boolean providerSync = true;

    public Pp2mpUnitProviderPortPart(IPartItem<?> partItem) {
        super(partItem, UnitPortType.PROVIDER, Pp2mpUnitPortPartModel.MODEL_PROVIDER);
        this.providerFilter = new Pp2mpProviderFilterData(
                this.getFilterMarkers(), this.getUpgrades(), this::saveProviderFilter
        );
        this.adjacentTarget = Pp2mpAdjacentTarget.forPart(this);
        this.sendTarget = new Pp2mpSendTarget(this.adjacentTarget, this::alertManager);
        this.providerTarget = new Pp2mpProviderTarget(
                this.sendTarget, this::getEffectiveProviderFilter, this::canProvide,
                this.actionSource, this::alertManager);
    }

    @Override
    protected void openMenu(Player player) {
        if (!this.isClientSide()) {
            MenuOpener.open(Pp2mpProviderFilterMenu.TYPE, player, MenuLocators.forPart(this));
        }
    }

    /* 将实际端口访问计入共享预算，计划执行时重新校验绑定。 */
    public ResourceTransferPlan planProvider(AEKey key, long amount, WorkBudget budget) {
        return this.providerTarget.plan(key, amount, budget);
    }

    private boolean canProvide() {
        Pp2mpUnitManagerPart manager = this.findManager();
        return manager != null && this.isBoundTo(manager) && manager.getLogic().isOperational();
    }

    /* 供应单元端口使用统一过滤菜单，并保留独立主界面的同步开关。 */
    @Override
    public AEBasePart getProviderFilterPart() {
        return this;
    }

    @Override
    public boolean isProviderFilterSynced() {
        return this.providerSync;
    }

    @Override
    public Consumer<Boolean> getProviderFilterSyncSetter() {
        return this::setProviderSync;
    }

    /* 仅同步规则，不覆盖端口本地标记和实际升级卡。 */
    @Override
    public Pp2mpProviderFilterData getEffectiveProviderFilter() {
        if (this.providerSync) {
            Pp2mpUnitManagerPart manager = this.findManager();
            if (manager != null && this.isBoundTo(manager)) {
                return manager.getEffectiveProviderFilter();
            }
        }
        return this.providerFilter;
    }

    public void setProviderSync(boolean synced) {
        if (this.providerSync != synced) {
            this.providerSync = synced;
            this.saveProviderFilter();
        }
    }

    private void saveProviderFilter() {
        if (this.getHost() != null) {
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }
        this.alertManager();
    }

    @Override
    protected void onFilterChanged() {
        super.onFilterChanged();
        if (this.providerFilter != null) {
            this.providerFilter.onChanged();
        }
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.providerFilter.writeLimits(data);
        data.putBoolean(PROVIDER_SYNC, this.providerSync);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.providerFilter.readLimits(data);
        // 旧存档没有同步字段，保留端口原本的本地配置。
        this.providerSync = data.getBoolean(PROVIDER_SYNC);
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.providerFilter.writeToStream(data);
        data.writeBoolean(this.providerSync);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean filterChanged = this.providerFilter.readFromStream(data);
        boolean previousSync = this.providerSync;
        this.providerSync = data.readBoolean();
        return changed || filterChanged || previousSync != this.providerSync;
    }

    @Override
    protected void clearAdjacentTarget() {
        this.adjacentTarget.clear();
        this.sendTarget.clear();
    }

}
