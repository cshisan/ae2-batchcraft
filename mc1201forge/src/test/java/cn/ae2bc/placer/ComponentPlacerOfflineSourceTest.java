package cn.ae2bc.placer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentPlacerOfflineSourceTest {
    @Test
    void itemAllowsOpeningWithoutAWirelessGridButStillRequiresPower() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/placer/ComponentPlacerItem.java"));

        int methodStart = source.indexOf("protected boolean checkPreconditions");
        int methodEnd = source.indexOf("public IGrid getLinkedGrid", methodStart);
        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains("player.level().isClientSide()"));
        assertTrue(method.contains("hasPower(player, 0.5, stack)"));
        assertFalse(method.contains("getLinkedGrid"));
    }

    @Test
    void hostKeepsLocalOnlyMenusOpenAndOnlyUsesWirelessLifecycleWhenConnected() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/placer/ComponentPlacerMenuHost.java"));

        assertTrue(source.contains("if (!getLinkStatus().connected())"));
        assertTrue(source.contains("return drainPower();"));
        assertTrue(source.contains("return super.onBroadcastChanges(menu);"));
    }

    @Test
    void placementKeepsAeStorageBeforeLocalMaterialFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/placer/ComponentPlacementService.java"));

        int extractionStart = source.indexOf("private static Reservation extract");
        int localStart = source.indexOf("ItemStack local =", extractionStart);
        int networkStart = source.indexOf("StorageHelper.poweredExtraction", extractionStart);
        assertTrue(networkStart >= extractionStart && networkStart < localStart);
    }
}
