package cn.ae2bc.placer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentPlacerPartValidationSourceTest {
    @Test
    void validatingMarkedPartsDoesNotInstantiateThem() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/placer/ComponentPlacerItem.java"));

        int methodStart = source.indexOf("public static boolean isUsablePart");
        int methodEnd = source.indexOf("public static boolean hasCraftingCard", methodStart);
        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains("partItem.getPartClass()"));
        assertFalse(method.contains("partItem.createPart()"));
    }

    @Test
    void outputRecoverySaveCallbackDoesNotResolveHostDuringConstruction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PTunnelOutputLogic.java"));

        assertTrue(source.contains(
                "new ExtractionRecoveryQueue(() -> output.getHost().markForSave())"));
        assertFalse(source.contains(
                "new ExtractionRecoveryQueue(output.getHost()::markForSave)"));
    }

    @Test
    void onlyPatternP2PInputsIgnoreTheCopiedFrequency() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/placer/ComponentPlacementService.java"));

        assertTrue(source.contains("placedPart instanceof P2PTunnelPart<?> tunnel"));
        assertTrue(source.contains("tunnel instanceof PatternP2PTunnelPart patternTunnel"));
        assertTrue(source.contains("|| patternTunnel.isOutput()"));
        assertTrue(source.contains("P2PService.get(grid).updateFreq(tunnel, frequency)"));
    }
}
