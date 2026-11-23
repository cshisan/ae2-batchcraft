package org.ae2bc.client.screen.page.access;

import org.ae2bc.core.part.OutputPageConfig;

import java.util.function.UnaryOperator;

public interface OutputAttrAccess {
    OutputPageConfig output();

    void updateOutput(UnaryOperator<OutputPageConfig> updater);
}
