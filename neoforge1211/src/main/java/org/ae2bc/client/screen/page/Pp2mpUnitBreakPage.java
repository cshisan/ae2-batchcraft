package org.ae2bc.client.screen.page;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.client.screen.page.access.UnitAttrAccess;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.screen.Route;

public final class Pp2mpUnitBreakPage extends AbstractPp2mpPage {
    private final UnitAttrAccess unitAccess;
    private final SyncAttrAccess syncAccess;
    private AE2Button enabledButton;
    private AE2Button disabledButton;

    public Pp2mpUnitBreakPage(UnitAttrAccess unitAccess, SyncAttrAccess syncAccess) {
        super(Route.UNIT_BREAK, 82, new FeatureComponent(
                Component.translatable("gui.ae2_batchcraft.page.unit.break.break_pickup.title"),
                43, 74, null
        ));
        this.unitAccess = unitAccess;
        this.syncAccess = syncAccess;
    }

    /* 初始化破坏拾取的启用和禁用按钮。 */
    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (this.enabledButton == null) {
            this.enabledButton = this.addButton(
                    "unit_break_pickup_enabled",
                    Component.translatable("gui.ae2_batchcraft.enabled"),
                    () -> this.setBreakPickup(true)
            );
            this.disabledButton = this.addButton(
                    "unit_break_pickup_disabled",
                    Component.translatable("gui.ae2_batchcraft.disabled"),
                    () -> this.setBreakPickup(false)
            );
        }
        this.layout();
        this.update();
    }

    /* 设置破坏拾取状态，保留其他单元配置。 */
    private void setBreakPickup(boolean enabled) {
        this.unitAccess.updateUnit(old -> new UnitPageConfig(
                enabled, old.outputPortMode(), old.redstonePortParam()
        ));
    }

    @Override
    public void update() {
        UnitPageConfig unitConfig = this.unitAccess.unit();
        boolean synced = this.syncAccess != null && this.syncAccess.isSyncEnabled();
        this.enabledButton.active = !synced && !unitConfig.breakPickup();
        this.disabledButton.active = !synced && unitConfig.breakPickup();
    }

    @Override
    public void layout() {
        if (this.enabledButton == null || this.disabledButton == null) {
            return;
        }
        this.setWidgetBounds(this.enabledButton, 12, 50, 71, 18);
        this.setWidgetBounds(this.disabledButton, 89, 50, 71, 18);
    }
}
