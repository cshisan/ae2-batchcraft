package org.ae2bc.client.screen.page.adapter;

import org.ae2bc.client.screen.page.access.OutputAttrAccess;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.OutputPageConfig;
import org.ae2bc.part.output.Pp2mpOutputMenu;

import java.util.function.UnaryOperator;

public class OutputPageAttrAdapter extends SyncedPageAttrAdapter implements OutputAttrAccess {
    private final Pp2mpOutputMenu menu;

    public OutputPageAttrAdapter(Pp2mpOutputMenu menu) {
        super(menu::isSyncSwitch, menu::switchChange, () -> menu.getConfig().general());
        this.menu = menu;
    }

    @Override
    public void updateGeneral(UnaryOperator<GeneralPageConfig> updater) {
        OutputConfig config = this.menu.getConfig();
        GeneralPageConfig general = updater.apply(config.general());
        this.menu.configChange(new OutputConfig(config.version(), general, config.output()));
    }

    @Override
    public OutputPageConfig output() {
        return this.menu.getConfig().output();
    }

    @Override
    public void updateOutput(UnaryOperator<OutputPageConfig> updater) {
        OutputConfig config = this.menu.getConfig();
        OutputPageConfig output = updater.apply(config.output());
        this.menu.configChange(new OutputConfig(config.version(), config.general(), output));
    }
}
