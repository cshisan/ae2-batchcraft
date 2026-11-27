package org.ae2bc.client.screen.page.adapter;

import org.ae2bc.client.screen.page.access.GeneralAttrAccess;
import org.ae2bc.client.screen.page.access.OutputAttrAccess;
import org.ae2bc.client.screen.page.access.UnitAttrAccess;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputPageConfig;
import org.ae2bc.core.part.UnitPageConfig;
import org.ae2bc.part.input.Pp2mpInputMenu;

import java.util.function.UnaryOperator;

public class InputPageAttrAdapter implements GeneralAttrAccess, OutputAttrAccess, UnitAttrAccess {
    private final Pp2mpInputMenu menu;

    public InputPageAttrAdapter(Pp2mpInputMenu menu) {
        this.menu = menu;
    }

    @Override
    public GeneralPageConfig general() {
        return menu.getConfig().general();
    }

    @Override
    public void updateGeneral(UnaryOperator<GeneralPageConfig> updater) {
        InputConfig config = menu.getConfig();
        GeneralPageConfig general = updater.apply(config.general());
        menu.configChange(new InputConfig(
                general,
                config.output(), config.unit(), config.taskAllocationMode(), config.taskSplitMode()
        ));
    }

    @Override
    public OutputPageConfig output() {
        return menu.getConfig().output();
    }

    @Override
    public void updateOutput(UnaryOperator<OutputPageConfig> updater) {
        InputConfig config = menu.getConfig();
        OutputPageConfig output = updater.apply(config.output());
        menu.configChange(new InputConfig(
                config.general(),
                output, config.unit(), config.taskAllocationMode(), config.taskSplitMode()
        ));
    }

    @Override
    public UnitPageConfig unit() {
        return menu.getConfig().unit();
    }

    @Override
    public void updateUnit(UnaryOperator<UnitPageConfig> updater) {
        InputConfig config = menu.getConfig();
        UnitPageConfig unit = updater.apply(config.unit());
        menu.configChange(new InputConfig(
                config.general(),
                config.output(), unit, config.taskAllocationMode(), config.taskSplitMode()
        ));
    }
}
