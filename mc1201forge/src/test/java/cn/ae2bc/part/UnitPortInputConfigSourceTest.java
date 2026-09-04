package cn.ae2bc.part;

import cn.ae2bc.core.unit.UnitPortType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitPortInputConfigSourceTest {
    @Test
    void inputTypePortSetContainsBreakCollectReturnAndExtract() {
        EnumSet<UnitPortType> inputTypes = Arrays.stream(UnitPortType.values())
                .filter(UnitPortType::returnsTaskOutput)
                .collect(() -> EnumSet.noneOf(UnitPortType.class), EnumSet::add, EnumSet::addAll);

        assertEquals(EnumSet.of(UnitPortType.BREAK, UnitPortType.COLLECT,
                UnitPortType.RETURN, UnitPortType.EXTRACT), inputTypes);
    }

    @Test
    void onlyInputTypePortsOpenTheInputConfiguration() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");
        String menu = read("src/main/java/cn/ae2bc/menu/UnitPortInputConfigMenu.java");

        assertTrue(port.contains("type.acceptsTaskInput() || type.returnsTaskOutput()"));
        assertTrue(port.contains("? UnitPortOutputConfigMenu.TYPE : UnitPortInputConfigMenu.TYPE"));
        assertTrue(menu.contains("if (!host.getType().returnsTaskOutput())"));
        assertFalse(menu.contains("setPriority"));
        assertFalse(menu.contains("setSingleSlot"));
    }

    @Test
    void allInputPathsApplyThePerPortMaterialFilter() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");

        assertTrue(port.contains("private boolean allowsInputFilter(AEKey what)"));
        assertTrue(port.contains("if (!type.returnsTaskOutput()) return true;"));
        assertTrue(port.contains("if (!allowsInputFilter(what))"));
        assertTrue(port.contains("return markers.isEmpty() || (inverted ? !marked : marked);"));
        assertTrue(port.contains("canReturnProductsInternally() && allowsInputFilter(what)"));
        assertTrue(port.contains("handleCollected(manager, what, amount, mode), entity"));
        assertTrue(port.contains("InputFilterMarkers"));
        assertTrue(port.contains("InputFilterInverter"));
    }

    @Test
    void inputAndOutputMarkersUseGenericAe2ConfigSlots() throws Exception {
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");
        String inputMenu = read("src/main/java/cn/ae2bc/menu/UnitPortInputConfigMenu.java");
        String outputMenu = read("src/main/java/cn/ae2bc/menu/UnitPortOutputConfigMenu.java");

        assertTrue(port.contains("private final GenericStackInv outputFilterMarkers;"));
        assertTrue(port.contains("private final GenericStackInv inputFilterMarkers;"));
        assertEquals(2, count(port, "new MarkerInventory(this::onFilterChanged, 18)"));
        assertTrue(port.contains("super(listener, Mode.CONFIG_TYPES, size)"));
        assertTrue(port.contains("new GenericStack(stack.what(), 0)"));
        assertTrue(inputMenu.contains("getInputFilterMarkers().createMenuWrapper()"));
        assertTrue(outputMenu.contains("getOutputFilterMarkers().createMenuWrapper()"));
        assertTrue(inputMenu.contains("new FakeSlot(markers, i), SlotSemantics.CONFIG"));
        assertTrue(outputMenu.contains("new FakeSlot(markers, i), SlotSemantics.CONFIG"));
        assertFalse(outputMenu.contains("class MarkerSlot"));
    }

    @Test
    void optionalJeiFallbackHandlesItemsAndFluids() throws Exception {
        String build = read("build.gradle");
        String mods = read("src/main/resources/META-INF/mods.toml");
        String plugin = read("src/main/java/cn/ae2bc/integration/jei/Ae2bcJeiPlugin.java");
        String handler = read("src/main/java/cn/ae2bc/integration/jei/UnitPortGhostIngredientHandler.java");

        assertTrue(build.contains("compileOnly fg.deobf(\"mezz.jei:jei-"));
        assertFalse(mods.contains("modId=\"jei\""));
        assertFalse(mods.contains("modId=\"ae2jeiintegration\""));
        assertTrue(plugin.contains("ModList.get().isLoaded(\"ae2jeiintegration\")"));
        assertTrue(handler.contains("VanillaTypes.ITEM_STACK.castIngredient"));
        assertTrue(handler.contains("ForgeTypes.FLUID_STACK.castIngredient"));
        assertTrue(handler.contains("GenericStack.wrapInItemStack(stack.what(), 0)"));
        assertTrue(handler.contains("getSlots(SlotSemantics.CONFIG)"));
    }

    private static int count(String text, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
