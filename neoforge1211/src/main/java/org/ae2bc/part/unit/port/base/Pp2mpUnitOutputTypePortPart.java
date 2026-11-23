package org.ae2bc.part.unit.port.base;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.jspecify.annotations.NonNull;

@Getter
public abstract class Pp2mpUnitOutputTypePortPart extends Pp2mpUnitIoTypePortPart implements IPriorityHost {
    public static final int MIN_PRIORITY = -9999;
    public static final int MAX_PRIORITY = 9999;

    protected final Pp2mpUnitOutputTypePortData data;

    public Pp2mpUnitOutputTypePortPart(IPartItem<?> partItem, UnitPortType type, Pp2mpUnitPortPartModel models) {
        super(partItem, type, models);
        this.data = new Pp2mpUnitOutputTypePortData(
                () -> this.getHost().markForSave(),
                () -> this.getHost().markForUpdate()
        );
    }

    @Override
    protected void openMenu(Player player) {
        if (!this.isClientSide()) {
            MenuOpener.open(Pp2mpUnitOutputTypePortMenu.TYPE, player, MenuLocators.forPart(this));
        }
    }

    /* 使用 AE2 优先级子页面的原生返回流程。 */
    @Override
    public void returnToMainMenu(@NonNull Player player, @NonNull ISubMenu subMenu) {
        MenuOpener.returnTo(Pp2mpUnitOutputTypePortMenu.TYPE, player, MenuLocators.forPart(this));
    }

    @Override
    public @NonNull ItemStack getMainMenuIcon() {
        return new ItemStack(this.getPartItem());
    }

    @Override
    public int getPriority() {
        return this.data.getPriority();
    }

    /* 优先级实际变化时才保存、刷新端口排序并唤醒管理器。 */
    @Override
    public void setPriority(int newValue) {
        int priority = Math.clamp(newValue,
                Pp2mpUnitOutputTypePortPart.MIN_PRIORITY, Pp2mpUnitOutputTypePortPart.MAX_PRIORITY);
        if (this.data.getPriority() == priority) {
            return;
        }
        this.data.update(() -> this.data.setPriority(priority));
        IGrid grid = this.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).portOrderChanged();
        }
        this.wakeManager();
    }

    public long output(Pp2mpUnitManagerPart manager, GenericStack stack, MaterialOutputMode form, Actionable mode) {
        if (!this.canOutput(manager, stack, form)
                || (mode == Actionable.MODULATE && !manager.getLogic().isTaskActive())) {
            return 0;
        }
        return this.outputToTarget(stack.what(), stack.amount(), mode);
    }

    /* 接单模拟和实际投递共用绑定、类型及过滤检查。 */
    protected final boolean canOutput(Pp2mpUnitManagerPart manager, GenericStack stack, MaterialOutputMode form) {
        boolean bound = this.isBoundTo(manager);
        if (!bound || stack == null || stack.amount() <= 0) {
            return false;
        }
        // 端口是否匹配、端口是否支持材料类型
        boolean formMatches = form != null && form.matchPort() == this.type;
        boolean supported = this.supports(stack.what());
        if (!formMatches || !supported) {
            return false;
        }
        return this.allowsFilter(stack.what());
    }

    protected abstract long outputToTarget(AEKey what, long amount, Actionable mode);

    /* 端口是否支持材料类型 */
    protected boolean supports(AEKey what) {
        return false;
    }

    /* 端口调度选项变化后唤醒所属单元管理器。 */
    public void wakeManager() {
        this.alertManager();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.data.writeToNBT(data);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.data.readFromNBT(data);

        IGrid grid = super.getMainNode().getGrid();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.data.writeToStream(data);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean attrChanged = this.data.readFromStream(data);
        return changed || attrChanged;
    }
}
