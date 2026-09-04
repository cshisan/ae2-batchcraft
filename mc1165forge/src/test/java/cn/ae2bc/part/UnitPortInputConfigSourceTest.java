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
    public void onlyInputTypePortsOpenTheInputConfiguration() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");
        String menu = read("src/main/java/cn/ae2bc/menu/UnitPortInputConfigMenu.java");

        assertTrue(port.contains("type.acceptsTaskInput() || type.returnsTaskOutput()"));
        assertTrue(port.contains("new UnitPortInputConfigMenu(id, inventory, this)"));
        assertTrue(menu.contains("result.getPortType().returnsTaskOutput()"));
        assertFalse(menu.contains("setPriority"));
        assertFalse(menu.contains("setSingleSlot"));
    }

    @Test
    public void allItemInputPathsUseThePerPortFilter() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");

        assertTrue(port.contains("public boolean allowsInputFilter(ItemStack stack)"));
        assertTrue(port.contains("if (!type.returnsTaskOutput()) return true;"));
        assertTrue(port.contains("candidate.isEmpty() || !allowsInputFilter(candidate)"));
        assertTrue(port.contains("offered.isEmpty() || !allowsInputFilter(offered)"));
        assertTrue(port.contains("!drop.isEmpty() && !allowsInputFilter(drop)"));
        assertTrue(port.contains("manager == null || !allowsInputFilter(stack)"));
        assertTrue(port.contains("InputFilterMarkers"));
        assertTrue(port.contains("InputFilterInverter"));
    }

    @Test
    public void inputScreenHasNoPriorityOrSingleSlotControls() throws Exception {
        String screen = read("src/main/java/cn/ae2bc/client/UnitPortInputConfigScreen.java");

        assertFalse(screen.contains("TextFieldWidget"));
        assertFalse(screen.contains("singleSlot"));
        assertTrue(screen.contains("imageHeight = 168"));
        assertTrue(screen.contains("getInputFilterInverter"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
