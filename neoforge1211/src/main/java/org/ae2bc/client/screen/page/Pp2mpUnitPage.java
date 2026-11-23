package org.ae2bc.client.screen.page;

import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.component.OutputPortModeButton;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.client.screen.page.access.UnitAttrAccess;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.core.screen.Route;
import org.ae2bc.core.unit.OutputPortMode;

public final class Pp2mpUnitPage extends AbstractPp2mpPage {
    private final UnitAttrAccess unitAccess;
    private final SyncAttrAccess syncAccess;
    private final OutputPortModeButton[] modeButtons = new OutputPortModeButton[4];

    public Pp2mpUnitPage(UnitAttrAccess unitAccess, SyncAttrAccess syncAccess) {
        super(Route.UNIT, 102, new FeatureComponent(
                Component.translatable("gui.ae2_batchcraft.page.unit.feeding_mode.title"),
                43, 94, null
        ));
        this.unitAccess = unitAccess;
        this.syncAccess = syncAccess;
    }

    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (this.modeButtons[0] == null) {
            for (OutputPortMode mode : OutputPortMode.values()) {
                OutputPortModeButton button = new OutputPortModeButton(mode, this::selectMode);
                this.modeButtons[mode.id] = this.addWidget(button.widgetId(), button, OutputPortModeButton.class);
            }
        }
        this.layout();
        this.update();
    }

    private void selectMode(OutputPortMode mode) {
        this.unitAccess.updateUnit(old -> new UnitPageConfig(old.breakPickup(), mode, old.redstonePortParam()));
    }

    @Override
    public void update() {
        boolean synced = this.syncAccess != null && this.syncAccess.isSyncEnabled();
        for (OutputPortModeButton button : this.modeButtons) {
            button.update(this.unitAccess.unit().outputPortMode(), synced);
        }
    }

    @Override
    public void layout() {
        for (int index = 0; index < this.modeButtons.length; index++) {
            this.setWidgetBounds(this.modeButtons[index], 12 + index % 2 * 78, 52 + index / 2 * 20, 74, 18);
        }
    }
}
