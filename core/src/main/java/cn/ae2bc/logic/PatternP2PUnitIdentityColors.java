package cn.ae2bc.logic;

import java.util.UUID;

/** Produces the four AE color indices used to identify one unit visually. */
public final class PatternP2PUnitIdentityColors {
    private PatternP2PUnitIdentityColors() {
    }

    public static short encode(UUID patternP2PUnitId) {
        if (patternP2PUnitId == null) {
            return 0;
        }
        long mixed = patternP2PUnitId.getMostSignificantBits() ^ patternP2PUnitId.getLeastSignificantBits();
        mixed ^= mixed >>> 32;
        mixed ^= mixed >>> 16;
        short encoded = (short) mixed;
        return encoded == 0 ? 1 : encoded;
    }
}
