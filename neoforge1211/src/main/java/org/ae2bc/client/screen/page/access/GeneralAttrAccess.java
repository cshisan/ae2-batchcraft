package org.ae2bc.client.screen.page.access;

import org.ae2bc.core.part.GeneralPageConfig;

import java.util.function.UnaryOperator;

public interface GeneralAttrAccess {
    GeneralPageConfig general();

    void updateGeneral(UnaryOperator<GeneralPageConfig> updater);
}
