package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakUnitPortEnchantmentsSourceTest {
    @Test
    void enchantmentsRoundTripThroughPlacementPersistenceAndDismantle() throws Exception {
        String source = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");

        assertTrue(source.contains("components.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)"));
        assertTrue(source.contains("builder.set(DataComponents.ENCHANTMENTS, enchantments)"));
        assertTrue(source.contains("ItemEnchantments.CODEC.parse"));
        assertTrue(source.contains("ItemEnchantments.CODEC.encodeStart"));
        assertTrue(source.contains("getBlockEntity(), enchantments,"));
        assertFalse(dismantleExportBody(source).contains("PatternP2PUnitId"));
        assertFalse(dismantleExportBody(source).contains("TransferPriority"));
        assertFalse(dismantleExportBody(source).contains("FilterMarkers"));
    }

    @Test
    void onlyBreakUnitPortItemsAreEnchantable() throws Exception {
        String item = read("src/main/java/cn/ae2bc/item/PatternP2PUnitPortItem.java");
        String registration = read("src/main/java/cn/ae2bc/registry/ModContent.java");

        assertTrue(item.contains("type == UnitPortType.BREAK"));
        assertTrue(registration.contains("new PatternP2PUnitPortItem(new Item.Properties(), type)"));
    }

    @Test
    void componentPlacerImportsComponentsFromTheReservedItems() throws Exception {
        String placer = read("src/main/java/cn/ae2bc/placer/ComponentPlacementService.java");

        assertTrue(placer.contains("cableItem, partReservation.stack()"));
        assertTrue(placer.contains("unitPort.getType() == UnitPortType.BREAK"));
        assertTrue(placer.contains("unitPort.importSettings(SettingsFrom.DISMANTLE_ITEM,"));
    }

    private static String dismantleExportBody(String source) {
        int start = source.indexOf("public void exportSettings(SettingsFrom from");
        int end = source.indexOf("private static void readFilterMarkers", start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
