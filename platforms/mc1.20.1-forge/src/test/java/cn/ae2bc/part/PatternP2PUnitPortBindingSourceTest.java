package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitPortBindingSourceTest {
    @Test
    void memoryCardBindingIncludesAndPersistsFrequency() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));

        assertTrue(source.contains("ItemData.hasP2PFrequency(cardData)"));
        assertTrue(source.contains("short requestedFrequency = ItemData.getP2PFrequency(cardData)"));
        assertTrue(source.contains("boundFrequency = requestedFrequency"));
        assertTrue(source.contains("boundFrequency = data.getShort(BOUND_FREQUENCY_TAG)"));
        assertTrue(source.contains("data.putShort(BOUND_FREQUENCY_TAG, boundFrequency)"));
        assertTrue(source.contains("data.writeShort(getBoundFrequency())"));
        assertTrue(source.contains("boundFrequency == 0 || boundFrequency == manager.getFrequency()"));
    }
}
