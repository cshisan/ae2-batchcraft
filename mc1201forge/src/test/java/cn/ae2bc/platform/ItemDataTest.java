package cn.ae2bc.platform;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDataTest {
    @Test
    void readsAe2P2PFrequency() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("p2pFreq", (short) 1234);

        assertTrue(ItemData.hasP2PFrequency(tag));
        assertEquals(1234, ItemData.getP2PFrequency(tag));
    }

    @Test
    void normalizesCompatibleP2PFrequency() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("freq", (short) 2345);

        ItemData.normalizeP2PFrequency(tag);

        assertEquals(2345, tag.getShort("p2pFreq"));
        assertTrue(ItemData.hasP2PFrequency(tag));
    }

    @Test
    void writesOnlyAe2P2PFrequency() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("freq", (short) 1);

        ItemData.setP2PFrequency(tag, (short) 3456);

        assertEquals(3456, tag.getShort("p2pFreq"));
        assertFalse(tag.contains("freq"));
    }
}
