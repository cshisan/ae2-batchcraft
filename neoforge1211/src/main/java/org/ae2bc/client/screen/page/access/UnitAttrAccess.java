package org.ae2bc.client.screen.page.access;

import org.ae2bc.core.part.UnitPageConfig;

import java.util.function.UnaryOperator;

public interface UnitAttrAccess {
    UnitPageConfig unit();

    void updateUnit(UnaryOperator<UnitPageConfig> updater);
}
