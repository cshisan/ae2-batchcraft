package org.ae2bc.pattern;

import net.minecraft.core.Direction;
import org.ae2bc.core.pattern.DirectionHelper;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MaterialOutputAttrTest {
    @Test
    void directionsAndModesRemainIndependentAndImmutable() {
        MaterialOutputAttr config = MaterialOutputAttr.EMPTY;
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            config = config.withDirection(slot, Direction.values()[slot % 6])
                    .withMode(slot, MaterialOutputMode.fromId(slot % 3));
        }
        MaterialOutputAttr restored = MaterialOutputAttr.fromPacked(config.toPacked());
        long[] exported = config.toPacked();
        exported[0] = 0;
        Assertions.assertEquals(config, restored);
        for (int slot = 0; slot < DirectionHelper.MAX_SLOTS; slot++) {
            Assertions.assertEquals(Direction.values()[slot % 6], config.direction(slot));
            Assertions.assertEquals(MaterialOutputMode.fromId(slot % 3), config.mode(slot));
        }
        Assertions.assertTrue(MaterialOutputAttr.EMPTY.isEmpty());
    }
}
