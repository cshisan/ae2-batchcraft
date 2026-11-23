package org.ae2bc.client.screen.page.adapter;

import org.ae2bc.client.screen.page.access.GeneralAttrAccess;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.core.part.GeneralPageConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class SyncedPageAttrAdapter implements SyncAttrAccess, GeneralAttrAccess {
    private final BooleanSupplier syncEnabled;
    private final Consumer<Boolean> setSyncEnabled;
    private final Supplier<GeneralPageConfig> general;

    protected SyncedPageAttrAdapter(
            BooleanSupplier syncEnabled, Consumer<Boolean> setSyncEnabled, Supplier<GeneralPageConfig> general
    ) {
        this.syncEnabled = syncEnabled;
        this.setSyncEnabled = setSyncEnabled;
        this.general = general;
    }

    @Override
    public final boolean isSyncEnabled() {
        return this.syncEnabled.getAsBoolean();
    }

    @Override
    public final void setSyncEnabled(boolean enabled) {
        this.setSyncEnabled.accept(enabled);
    }

    @Override
    public final GeneralPageConfig general() {
        return this.general.get();
    }
}
