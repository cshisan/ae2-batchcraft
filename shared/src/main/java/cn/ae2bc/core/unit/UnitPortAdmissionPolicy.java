package cn.ae2bc.core.unit;

/** Platform-neutral admission rules shared by unit manager implementations. */
public final class UnitPortAdmissionPolicy {
    private UnitPortAdmissionPolicy() {
    }

    public static long initialCapacity(UnitPortType type, TransferPortOutputMode mode) {
        return usesTransferOutputMode(type) && mode == TransferPortOutputMode.SINGLE_ITEM
                ? 1 : Long.MAX_VALUE;
    }

    public static boolean usesTransferOutputMode(UnitPortType type) {
        return type == UnitPortType.TRANSFER;
    }
}
