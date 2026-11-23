package org.ae2bc.part;

import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.unit.UnitIdentityKey;

import java.util.Random;
import java.util.function.Predicate;

public interface Pp2mpKey {
    static int key(short frequency, short unitId) {
        return UnitIdentityKey.pack(frequency, unitId);
    }

    static short newUnitId(Random generator, Runnable rebuild, Predicate<Short> exist) {
        rebuild.run();

        short newUnitId;
        int cycles = 0;

        do {
            newUnitId = (short) generator.nextInt(1 << 16);
            cycles++;
        } while (newUnitId == 0 || exist.test(newUnitId));

        if (cycles > 25) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug(
                        "Generating a new pattern P2P unit ID '{}' took {} cycles",
                        Short.toUnsignedInt(newUnitId), cycles
                );
            }
        }

        return newUnitId;
    }
}
