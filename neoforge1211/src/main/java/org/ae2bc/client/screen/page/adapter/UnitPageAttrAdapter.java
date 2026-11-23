package org.ae2bc.client.screen.page.adapter;

import org.ae2bc.client.screen.page.access.UnitAttrAccess;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerMenu;

import java.util.function.UnaryOperator;

public class UnitPageAttrAdapter extends SyncedPageAttrAdapter implements UnitAttrAccess {
    private final Pp2mpUnitManagerMenu menu;

    public UnitPageAttrAdapter(Pp2mpUnitManagerMenu menu) {
        super(menu::isSyncSwitch, menu::switchChange, () -> menu.getConfig().general());
        this.menu = menu;
    }

    @Override
    public void updateGeneral(UnaryOperator<GeneralPageConfig> updater) {
        UnitConfig config = this.menu.getConfig();
        GeneralPageConfig general = updater.apply(config.general());
        this.menu.configChange(new UnitConfig(config.version(), general, config.unit()));
    }

    @Override
    public UnitPageConfig unit() {
        return this.menu.getConfig().unit();
    }

    @Override
    public void updateUnit(UnaryOperator<UnitPageConfig> updater) {
        UnitConfig config = this.menu.getConfig();
        UnitPageConfig unit = updater.apply(config.unit());
        this.menu.configChange(new UnitConfig(config.version(), config.general(), unit));
    }
}
