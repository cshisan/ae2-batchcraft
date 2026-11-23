package org.ae2bc.core.unit;

public final class UnitIdentityKey {
    private UnitIdentityKey() {
    }

    public static int pack(short frequency, short unitId) {
        return Short.toUnsignedInt(frequency) << Short.SIZE | Short.toUnsignedInt(unitId);
    }
}
