package cn.ae2bc.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class GuiRenderingSourceTest {
    @Test
    public void taskResetControlsUseVisibleButtons() throws Exception {
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        String confirmation = readClient("TaskResetConfirmation.java");

        assertTrue(tunnel.contains("new Ae2IconButton(6, guiLeft + xSize + 2"));
        assertTrue(tunnel.contains("new Ae2Button(43, guiLeft + 12, guiTop + 92"));
        assertTrue(manager.contains("new Ae2IconButton(14, guiLeft + xSize + 2"));
        assertTrue(confirmation.contains("new GuiYesNo"));
        assertTrue(confirmation.contains("if (confirmed)"));
        assertTrue(tunnel.contains("reset_task.confirm.input"));
        assertTrue(tunnel.contains("reset_task.confirm.output"));
        assertTrue(manager.contains("reset_task.confirm.unit"));
    }

    @Test
    public void textFieldsUseAeControlsWithVisibleBackgrounds() throws Exception {
        assertVisibleAeNumberField(readClient("PatternP2PTunnelScreen.java"));
        assertVisibleAeNumberField(readClient("PatternP2PUnitManagerScreen.java"));
        assertVisibleAeNumberField(readClient("UnitPortOutputConfigScreen.java"));
        String placer = readClient("ComponentPlacerCraftAmountScreen.java");
        assertFalse(placer.contains("setTextColor"));
        assertFalse(placer.contains("setDisabledTextColour"));
    }

    @Test
    public void legacyScreensUseAeNumberFields() throws Exception {
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        String outputConfig = readClient("UnitPortOutputConfigScreen.java");
        assertTrue(tunnel.contains("GuiNumberBox"));
        assertTrue(manager.contains("GuiNumberBox"));
        assertTrue(outputConfig.contains("GuiNumberBox"));
        assertTrue(readClient("ComponentPlacerCraftAmountScreen.java").contains("GuiNumberBox"));
        assertFalse(tunnel.contains("drawNumberFieldBackground"));
        assertFalse(manager.contains("drawNumberFieldBackground"));
        assertFalse(outputConfig.contains("drawNumberFieldBackground"));
    }

    @Test
    public void numberFieldsShareTextBaselinesWithLabels() throws Exception {
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        for (String source : new String[] { tunnel, manager }) {
            assertTrue(source.contains("new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 96, 36"));
            assertTrue(source.contains("new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 117, 36"));
            assertTrue(source.contains("new GuiNumberBox(fontRenderer, guiLeft + 104, guiTop + 138, 36"));
            assertFalse(source.contains("private GuiNumberBox field("));
        }
    }

    @Test
    public void iconButtonsRestoreRenderColorState() throws Exception {
        String icons = readClient("Ae2IconButton.java");
        assertTrue(icons.contains("GlStateManager.pushAttrib()"));
        assertTrue(icons.contains("GlStateManager.popAttrib()"));
    }

    @Test
    public void legacyToolbarButtonsUseAeIcons() throws Exception {
        String icons = readClient("Ae2IconButton.java");
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        String outputConfig = readClient("UnitPortOutputConfigScreen.java");
        assertTrue(icons.contains("PERMISSION_BUILD = 179"));
        assertTrue(icons.contains("FULLNESS_HALF = 81"));
        assertTrue(icons.contains("PERMISSION_CRAFT = 178"));
        assertTrue(icons.contains("ICON_128 = 128"));
        assertTrue(icons.contains("INVALID = 129"));
        assertTrue(icons.contains("BLOCK_NO = 20"));
        assertTrue(icons.contains("BLOCK_YES = 21"));
        assertTrue(tunnel.contains("pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD)"));
        assertTrue(tunnel.contains("pageIconButton(3, Page.TRANSFER, 42, Ae2IconButton.FULLNESS_HALF)"));
        assertTrue(tunnel.contains("pageIconButton(4, Page.BREAK, 64, Ae2IconButton.PERMISSION_CRAFT)"));
        assertTrue(manager.contains("pageIconButton(2, Page.COMMON, 20, Ae2IconButton.PERMISSION_BUILD)"));
        assertTrue(manager.contains("pageIconButton(3, Page.TRANSFER, 42, Ae2IconButton.FULLNESS_HALF)"));
        assertTrue(manager.contains("pageIconButton(4, Page.BREAK, 64, Ae2IconButton.PERMISSION_CRAFT)"));
        assertTrue(tunnel.contains("Ae2IconButton.ICON_128"));
        assertTrue(manager.contains("Ae2IconButton.ICON_128"));
        assertTrue(outputConfig.contains("new Ae2IconButton(4, guiLeft - 24, guiTop + 19"));
        assertFalse(outputConfig.contains("DisabledStateAe2IconButton"));
        assertTrue(outputConfig.contains("Ae2IconButton.BLOCK_YES"));
        assertTrue(outputConfig.contains("Ae2IconButton.BLOCK_NO"));
        assertTrue(outputConfig.contains("setIconIndex(menu.singleSlot ? Ae2IconButton.BLOCK_YES : Ae2IconButton.BLOCK_NO)"));
    }

    @Test
    public void outputPriorityButtonsFollowNumberField() throws Exception {
        String outputConfig = readClient("UnitPortOutputConfigScreen.java");
        assertTrue(outputConfig.contains("guiTop + 56, 59"));
        assertTrue(outputConfig.contains("guiTop + 68, 32, 18"));
        assertFalse(outputConfig.contains("guiTop + 77, 32, 18"));
        assertTrue(outputConfig.contains("MARKER_TOP = 102"));
        assertTrue(outputConfig.contains("PLAYER_INVENTORY_TOP = 150"));
        assertTrue(outputConfig.contains("HOTBAR_TOP = 208"));
    }

    @Test
    public void unitManagerPageIdsMapExplicitly() throws Exception {
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        assertTrue(manager.contains("case 5: return Page.REDSTONE;"));
        assertTrue(manager.contains("case 6: return Page.ENERGY;"));
        assertTrue(manager.contains("Page target = pageForButtonId(button.id);"));
        assertTrue(manager.contains("ySize = resolveHeight(page);"));
        assertTrue(manager.contains("guiTop = relayoutTop;"));
        assertTrue(readClient("PatternP2PTunnelScreen.java").contains("guiTop = relayoutTop;"));
        assertFalse(manager.contains("button == redstonePageButton"));
        assertFalse(manager.contains("button == energyPageButton"));
    }

    @Test
    public void energyPageUsesDashedSection() throws Exception {
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        assertTrue(manager.contains("section(\"gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration\", 43, 74)"));
        assertTrue(manager.contains("title(\"gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration\", 39)"));
    }

    @Test
    public void inventorySlotsRestoreTheirTextureBeforeDrawing() throws Exception {
        String outputConfig = readClient("UnitPortOutputConfigScreen.java");
        String menu = read("src/main/java/cn/ae2bc/menu", "UnitPortOutputConfigMenu.java");
        String skin = readClient("Ae2GuiSkin.java");
        assertTrue(outputConfig.contains("bindTexture(INVENTORY_TEXTURE)"));
        assertTrue(outputConfig.contains("drawInterfaceUpgradePanel"));
        assertFalse(outputConfig.contains("drawUpgradePanel"));
        assertTrue(menu.contains("new InverterSlot(part.getOutputFilterInverter()"));
        assertTrue(menu.contains("class InverterSlot extends SlotRestrictedInput"));
        assertTrue(menu.contains("class MarkerSlot extends SlotFake"));
        assertTrue(menu.contains("super(PlacableItemType.UPGRADES"));
        assertTrue(menu.contains("extends AEBaseContainer"));
        assertTrue(menu.contains("bindPlayerInventory(player.inventory, 0, 150)"));
        assertTrue(menu.contains("8 + i % 9 * 18, 102 + i / 9 * 18"));
        assertTrue(menu.contains("setNotDraggable()"));
        assertTrue(skin.contains("35, 14 + Math.max(0, upgradeSlots) * 18"));
        assertFalse(skin.contains("drawNumberFieldBackground"));
        assertTrue(readPart("PatternP2PUnitPortPart.java")
                .contains("cardInverter().isSameAs(stack)"));
    }

    @Test
    public void outputConfigUsesPlainButtonWithoutToolbarBackground() throws Exception {
        String outputConfig = readClient("UnitPortOutputConfigScreen.java");
        assertTrue(outputConfig.contains("private Ae2IconButton singleSlotButton;"));
        assertTrue(outputConfig.contains("new Ae2IconButton(4, guiLeft - 24, guiTop + 19"));
        assertTrue(outputConfig.contains("if (!menu.singleSlotEditable) return;"));
        assertFalse(outputConfig.contains("DisabledStateAe2IconButton"));
        assertFalse(readClient("Ae2GuiSkin.java").contains("drawDisabledToolbarButton"));
        assertFalse(outputConfig.contains("Ae2GuiSkin.draw(guiLeft - 24, guiTop + 19, 24, 24)"));
    }

    @Test
    public void transferPagesUseThreeExplicitModeButtons() throws Exception {
        String tunnel = readClient("PatternP2PTunnelScreen.java");
        String manager = readClient("PatternP2PUnitManagerScreen.java");
        assertTrue(tunnel.contains("for (TransferPortOutputMode mode : TransferPortOutputMode.values())"));
        assertTrue(manager.contains("for (TransferPortOutputMode mode : TransferPortOutputMode.values())"));
        assertTrue(tunnel.contains("button.id >= 25 && button.id <= 27"));
        assertTrue(manager.contains("button.id >= 25 && button.id <= 27"));
    }

    @Test
    public void inputOwnsAndSynchronizesSingleSlotMode() throws Exception {
        String screen = readClient("PatternP2PTunnelScreen.java");
        String part = readPart("PatternP2PTunnelPart.java");
        assertTrue(screen.contains("slotSharingMode = settings.getOutputSlotSharingMode()"));
        assertTrue(screen.contains("transferPortOutputMode, slotSharingMode"));
        assertTrue(part.contains("Ae2bcOutputSlotSharingMode"));
        assertTrue(part.contains("settings.getOutputSlotSharingMode().getId()"));
        assertTrue(part.contains("left.getOutputSlotSharingMode() == right.getOutputSlotSharingMode()"));
    }

    @Test
    public void chineseSingleSlotTooltipUsesFullWidthPunctuation() throws Exception {
        String language = read("src/main/resources/assets/ae2_batchcraft/lang", "zh_cn.lang");
        String tooltip = line(language,
                "gui.ae2_batchcraft.unit_port_output_config.single_slot.tooltip=");
        assertTrue(tooltip.contains("当前："));
        assertTrue(tooltip.contains("开启后，"));
        assertFalse(tooltip.contains("当前:"));
        assertFalse(tooltip.contains("开启后,"));
    }

    @Test
    public void tunnelCollisionMatchesAUnitPort() throws Exception {
        String tunnel = readPart("PatternP2PTunnelPart.java");
        String unitPort = readPart("PatternP2PUnitPortPart.java");
        String box = "helper.addBox(3, 3, 13, 13, 13, 16)";
        String cableLength = "getCableConnectionLength(AECableType cable) { return 1; }";

        assertTrue(tunnel.contains(box));
        assertTrue(unitPort.contains(box));
        assertTrue(tunnel.contains(cableLength));
        assertTrue(unitPort.contains(cableLength));
    }

    private static void assertVisibleAeNumberField(String source) {
        assertTrue(source.contains("setEnableBackgroundDrawing(true)"));
        assertTrue(source.contains("setMaxStringLength(16)"));
        assertTrue(source.contains("setVisible(true)"));
        assertTrue(source.contains("setTextColor(0xFFFFFF)"));
        assertFalse(source.contains("setDisabledTextColour(0xFFFFFF)"));
        assertFalse(source.contains("setValidator"));
    }

    private static String readClient(String name) throws Exception {
        return read("src/main/java/cn/ae2bc/client", name);
    }

    private static String readPart(String name) throws Exception {
        return read("src/main/java/cn/ae2bc/part", name);
    }

    private static String read(String directory, String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(directory, name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("Missing source section");
        return source.substring(from, to);
    }

    private static String line(String source, String prefix) {
        for (String line : source.split("\\r?\\n")) {
            if (line.startsWith(prefix)) return line;
        }
        throw new AssertionError("Missing language line " + prefix);
    }
}
