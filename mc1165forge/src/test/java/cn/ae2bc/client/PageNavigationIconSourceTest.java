package cn.ae2bc.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class PageNavigationIconSourceTest {
    @Test
    public void navigationArrowsUseLocalOpticalOffsets() throws Exception {
        String icons = readClient("Ae2IconButton.java");
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");

        assertTrue(icons.contains("x + 2 + offset + iconOffsetX"));
        assertTrue(icons.contains("y + 2 + offset + iconOffsetY"));
        assertTrue(tunnel.contains("Icon.ARROW_RIGHT.ordinal(), 1.0F, -1, -1"));
        assertTrue(tunnel.contains(
                "4, nextUnitPortPage(), 64, Icon.ARROW_RIGHT.ordinal(), -1, -1"));
        assertTrue(manager.contains("pagePortButton = addButton(new Ae2IconButton"));
        assertTrue(manager.contains("Icon.ARROW_RIGHT.ordinal(), 1.0F, -1, -1"));
    }

    @Test
    public void generalPagesUseTheDisabledBuildPermissionIcon() throws Exception {
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");

        assertTrue(tunnel.contains("Icon.PERMISSION_BUILD_DISABLED.ordinal()"));
        assertTrue(manager.contains("Icon.PERMISSION_BUILD_DISABLED.ordinal()"));
        assertFalse(tunnel.contains("Icon.FULLNESS_FULL.ordinal()"));
        assertFalse(manager.contains("Icon.FULLNESS_FULL.ordinal()"));
    }

    private static String readClient(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/client", name)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
