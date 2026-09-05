package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public final class UnitPortInputConfigSourceTest {
    @Test
    public void inputPortsUseTheIndependentInputGuiRange() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");
        String mod = read("src/main/java/cn/ae2bc/Ae2bcMod.java");
        String menu = read("src/main/java/cn/ae2bc/menu/UnitPortInputConfigMenu.java");

        assertTrue(port.contains("type.acceptsTaskInput() || type.returnsTaskOutput()"));
        assertTrue(port.contains("GUI_UNIT_PORT_INPUT_BASE"));
        assertTrue(mod.contains("GUI_UNIT_PORT_INPUT_BASE = 4600"));
        assertTrue(menu.contains("result.getPortType().returnsTaskOutput()"));
        assertFalse(menu.contains("setPriority"));
        assertFalse(menu.contains("setSingleSlot"));
    }

    @Test
    public void allItemInputPathsUseThePerPortFilter() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");
        String strategy = read("src/main/java/cn/ae2bc/platform/AnnihilationPlaneBreakStrategy.java");

        assertTrue(port.contains("public boolean allowsInputFilter(ItemStack stack)"));
        assertTrue(port.contains("candidate.isEmpty() || !allowsInputFilter(candidate)"));
        assertTrue(port.contains("offered.isEmpty() || !allowsInputFilter(offered)"));
        assertTrue(port.contains("manager == null || !allowsInputFilter(stack)"));
        assertTrue(strategy.contains("!port.allowsInputFilter(drop)"));
        assertTrue(strategy.contains("manager.returnProduct(drop, false)"));
        assertTrue(port.contains("InputFilterMarkers"));
        assertTrue(port.contains("InputFilterInverter"));
    }

    @Test
    public void inputScreenContainsOnlyMarkersUpgradeAndInventory() throws Exception {
        String screen = read("src/main/java/cn/ae2bc/client/UnitPortInputConfigScreen.java");

        assertFalse(screen.contains("GuiNumberBox"));
        assertFalse(screen.contains("singleSlot"));
        assertTrue(screen.contains("MARKER_TOP = 36"));
        assertTrue(screen.contains("PLAYER_INVENTORY_TOP = 84"));
        assertTrue(screen.contains("HOTBAR_TOP = 142"));
        assertTrue(screen.contains("getInputFilterInverter"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
