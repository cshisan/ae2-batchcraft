package org.ae2bc.part.unit.port.base;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.storage.ReceiveInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class Pp2mpUnitInputTypePortPart extends Pp2mpUnitIoTypePortPart {
    protected final @Getter ReceiveInventory receiveInventory;
    private @Nullable Pp2mpUnitManagerPart returnOwner;

    public Pp2mpUnitInputTypePortPart(IPartItem<?> partItem, UnitPortType type, Pp2mpUnitPortPartModel models) {
        super(partItem, type, models);
        this.receiveInventory = new ReceiveInventory(
                () -> {
                    Pp2mpUnitManagerPart manager = this.receiveOwner();
                    return manager == null || !this.isBoundTo(manager) ? null : manager.findReceiveInventory();
                },
                this::findReceiveTarget,
                this::allowsReceived,
                this::onReceived,
                this.getActionSource(),
                this::alertManager,
                this::beginReceive, this::endReceive
        );
    }

    private @Nullable Pp2mpUnitManagerPart receiveOwner() {
        return this.returnOwner == null ? this.findManager() : this.returnOwner;
    }

    private void beginReceive() {
        this.returnOwner = this.findManager();
        if (this.returnOwner != null) {
            this.returnOwner.getLogic().beginReturn();
        }
    }

    private void endReceive() {
        Pp2mpUnitManagerPart manager = this.returnOwner;
        this.returnOwner = null;
        if (manager != null) {
            manager.getLogic().endReturn();
        }
    }

    /* 输入型端口共用管理器的返回目标，不在端口中保存产物。 */
    @SuppressWarnings("UnstableApiUsage")
    protected Map<AEKeyType, ExternalStorageStrategy> findReceiveTarget() {
        Pp2mpUnitManagerPart manager = this.receiveOwner();
        return manager == null || !this.isBoundTo(manager) ? Map.of() : manager.findReceiveStrategies();
    }

    protected boolean allowsReceived(AEKey key) {
        Pp2mpUnitManagerPart manager = this.receiveOwner();
        return manager != null && this.isBoundTo(manager)
                && this.allowsFilter(key)
                && manager.getLogic().canAcceptReturned(key);
    }

    /* 仅把实际返回量交给管理器推进任务。 */
    protected void onReceived(AEKey key, long amount) {
        Pp2mpUnitManagerPart manager = this.receiveOwner();
        if (manager != null && this.isBoundTo(manager)) {
            manager.getLogic().onReturnedInserted(key, amount);
        }
    }

    @Override
    protected void openMenu(Player player) {
        if (!this.isClientSide()) {
            MenuOpener.open(Pp2mpUnitInputTypePortMenu.TYPE, player, MenuLocators.forPart(this));
        }
    }
}
