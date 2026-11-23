package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import org.jspecify.annotations.Nullable;

public interface Pp2mpPart {
    IManagedGridNode getMainNode();

    short getFrequency();

    default @Nullable IGrid grid() {
        return this.getMainNode().getGrid();
    }

    default boolean versionEq(short v1, short v2) {
        return v1 != 0 && v1 == v2;
    }

    default boolean validFrequency() {
        return this.getFrequency() != 0;
    }

    default boolean noFrequency() {
        return this.getFrequency() == 0;
    }
}
