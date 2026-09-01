package cn.ae2bc.placer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class ComponentPlacerUpgradeSourceTest {
    @Test
    public void onlyOneCraftingCardSlotIsSupported() throws Exception {
        String item = read("src/main/java/cn/ae2bc/placer/ComponentPlacerItem.java");
        String menu = read("src/main/java/cn/ae2bc/menu/ComponentPlacerMenu.java");
        String mod = read("src/main/java/cn/ae2bc/Ae2bcMod.java");
        String screen = read("src/main/java/cn/ae2bc/client/ComponentPlacerScreen.java");

        assertTrue(item.contains("new ComponentPlacerInventory(stack, UPGRADES_KEY, 1, 1"));
        assertTrue(item.contains("candidate.isEmpty() || isCraftingCard(candidate)"));
        assertFalse(item.contains("Upgrades.CAPACITY"));
        assertFalse(item.contains("getAEMaxPower(ItemStack stack)"));
        assertFalse(mod.contains("Upgrades.CAPACITY.registerItem"));
        assertTrue(mod.contains("Upgrades.CRAFTING.registerItem"));
        assertTrue(menu.contains("new SlotItemHandler(upgrades, 0, 187, 8)"));
        assertTrue(screen.contains("UPGRADE_PANEL_HEIGHT = 32"));
        assertTrue(screen.contains("drawInterfaceUpgradePanel(guiLeft, guiTop, 177, 1)"));
    }

    @Test
    public void legacyUpgradeCardsAreReturnedInsteadOfDiscarded() throws Exception {
        String item = read("src/main/java/cn/ae2bc/placer/ComponentPlacerItem.java");
        String menu = read("src/main/java/cn/ae2bc/menu/ComponentPlacerMenu.java");

        assertTrue(item.contains("migrateLegacyUpgrades"));
        assertTrue(item.contains("returned.add(candidate.copy())"));
        assertTrue(item.contains("normalized.setInteger(\"Size\", 1)"));
        assertTrue(menu.contains("player.inventory.addItemStackToInventory(returned)"));
        assertTrue(menu.contains("player.dropItem(returned, false)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
