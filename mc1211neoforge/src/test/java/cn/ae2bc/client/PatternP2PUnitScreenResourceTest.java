package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitScreenResourceTest {
    private static final String MANAGER_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/pattern_p2p_unit_manager.json";
    private static final String INPUT_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json";
    private static final String OUTPUT_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_output.json";
    private static final String PRODUCT_EXTRACTION_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/product_extraction.json";
    private static final String OUTPUT_PORT_CONFIG_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/unit_port_output_config.json";
    private static final String INPUT_PORT_CONFIG_SCREEN =
            "assets/ae2/screens/ae2_batchcraft/unit_port_input_config.json";

    @Test
    void configurationScreensExposeTheSharedPageNavigation() throws Exception {
        assertPagedConfigurationScreen(MANAGER_SCREEN);
        assertPagedConfigurationScreen(INPUT_SCREEN);
        assertPagedConfigurationScreen(OUTPUT_SCREEN);
    }

    @Test
    void taskEndpointsExposeTheResetTaskButton() throws Exception {
        assertTrue(readText(MANAGER_SCREEN).contains("\"rightToolbar\""));
        assertTrue(readText(INPUT_SCREEN).contains("\"rightToolbar\""));
        assertTrue(readText(OUTPUT_SCREEN).contains("\"rightToolbar\""));
        org.junit.jupiter.api.Assertions.assertFalse(readText(OUTPUT_SCREEN).contains("\"resetTask\":"));
        String outputSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelOutputScreen.java"));
        assertTrue(outputSource.contains("new RightToolbarIconButton(appeng.client.gui.Icon.SCHEDULING_DEFAULT"));
    }

    @Test
    void inputCommonPageContainsExtractionControlsWithoutFilterSlots() throws Exception {
        String screen = readText(INPUT_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java"));

        assertTrue(screen.contains("\"productExtractionEnabled\""));
        assertTrue(screen.contains("\"productExtractionDisabled\""));
        assertTrue(screen.contains("\"extraction_interval\""));
        assertTrue(screen.contains("\"extraction_amount\""));
        assertTrue(source.contains("widgets.addButton(\"productExtractionEnabled\""));
        assertTrue(source.contains("widgets.addButton(\"productExtractionDisabled\""));
        assertTrue(source.contains("gui.ae2_batchcraft.enabled"));
        assertTrue(source.contains("gui.ae2_batchcraft.disabled"));
        org.junit.jupiter.api.Assertions.assertFalse(
                source.contains("productExtraction = new VerticallyAlignedCheckbox"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("PRODUCT_MARKER"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("whitelist"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("blacklist"));
    }

    @Test
    void managerCommonPageContainsUnitExtractionTimingWithoutAnEnableSwitch() throws Exception {
        String screen = readText(MANAGER_SCREEN);

        assertTrue(screen.contains("\"extraction_interval\""));
        assertTrue(screen.contains("\"extraction_amount\""));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("\"productExtraction\""));
    }

    @Test
    void unitCommonPageUsesThreeExplicitSingleSlotModes() throws Exception {
        String input = readText(INPUT_SCREEN);
        String manager = readText(MANAGER_SCREEN);
        String inputSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java"));
        String managerSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PUnitManagerScreen.java"));

        for (String screen : new String[]{input, manager}) {
            assertTrue(screen.contains("\"singleSlotAll\""));
            assertTrue(screen.contains("\"singleSlotDisabled\""));
            assertTrue(screen.contains("\"singleSlotFollowPort\""));
            org.junit.jupiter.api.Assertions.assertFalse(screen.contains("\"singleSlotMode\""));
        }
        for (String source : new String[]{inputSource, managerSource}) {
            assertTrue(source.contains("Map<OutputSlotSharingMode, AE2Button> singleSlotModeButtons"));
            assertTrue(source.contains("widgets.addButton(\"singleSlot\" + camel(mode.getSerializedName())"));
            assertTrue(source.contains("menu.setOutputSlotSharingMode(mode)"));
        }
        assertTrue(inputSource.contains(
                "entry.getValue().active = entry.getKey() != menu.outputSlotSharingMode"));
        assertTrue(managerSource.contains(
                "entry.getValue().active = editable && entry.getKey() != menu.outputSlotSharingMode"));
    }

    @Test
    void extractionFieldsUseTheFixedRightAlignedHorizontalPosition() throws Exception {
        String controls = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/ProductExtractionControls.java"));
        assertTrue(controls.contains("int inputX = left + 104;"));
        assertTrue(controls.contains("inputX, top + 92"));
        assertTrue(controls.contains("inputX, top + 113"));
        for (String sourcePath : new String[]{
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java",
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelOutputScreen.java",
                "src/main/java/cn/ae2bc/client/PatternP2PUnitManagerScreen.java"}) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(sourcePath));
            assertTrue(source.contains("ProductExtractionControls.create"), sourcePath);
        }
    }

    @Test
    void productExtractionCardUsesTheSharedUnitLabel() throws Exception {
        String screen = readText(PRODUCT_EXTRACTION_SCREEN);

        assertTrue(screen.contains("gui.ae2_batchcraft.product_extraction.unit"));
        org.junit.jupiter.api.Assertions.assertFalse(
                screen.contains("gui.ae2_batchcraft.product_extraction.item"));
    }

    @Test
    void pageToolbarUsesDynamicHeightAndTheSelectedAe2Icons() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PUnitPagedScreen.java"));

        assertTrue(source.contains("style.getWidget(\"verticalToolbar\")"));
        assertTrue(source.contains("toolbarStyle.getTop()"));
        assertTrue(source.contains("getToolbarHeightFromAe2"));
        assertTrue(source.contains("TOOLBAR_BACKGROUND_BOTTOM_PADDING"));
        assertTrue(source.contains("new ScaledIconButton(Icon.ARROW_RIGHT"));
        assertTrue(source.contains("case OUTPUT_COMMON, UNIT_COMMON -> Icon.S_MACHINE"));
        assertTrue(source.contains("case Page.OUTPUT_COMMON, Page.UNIT_COMMON -> 0.6f;"));
        assertTrue(source.contains("new RightToolbarPanel()"));
        assertTrue(source.contains("widgets.add(\"rightToolbar\", rightToolbar)"));
        assertTrue(source.contains("rightToolbar.addButton(button)"));
        String panelSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/RightToolbarPanel.java"));
        assertTrue(panelSource.contains("int x = position.getX()"));
        assertTrue(panelSource.contains("PANEL_WIDTH = 21"));
        assertTrue(panelSource.contains("PANEL_X_OFFSET = 1"));
        assertTrue(panelSource.contains("BOTTOM_PADDING = 4"));
        assertTrue(panelSource.contains("ICON_BUTTON_RENDER_EXTRA = 5"));
        assertTrue(panelSource.contains("button.getHeight() + VERTICAL_SPACING"));
        assertTrue(panelSource.contains("vertical_buttons_bg"));
        assertTrue(panelSource.contains("vertical_buttons_bg_right"));
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/resources/assets/ae2/textures/gui/sprites/vertical_buttons_bg_right.png")));
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/resources/assets/ae2/textures/gui/sprites/vertical_buttons_bg_right.png.mcmeta")));
        assertTrue(panelSource.contains("List<IconButton> buttons"));
        String scaledButtonSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/ScaledIconButton.java"));
        assertTrue(scaledButtonSource.contains("iconScale"));
        assertTrue(scaledButtonSource.contains("iconOffsetX"));
        assertTrue(scaledButtonSource.contains("iconOffsetY"));
        assertTrue(scaledButtonSource.contains("Math.round(iconBoxSize * iconScale)"));
        assertTrue(scaledButtonSource.contains("if (!halfSize && (size & 1) != 0)"));
        assertTrue(scaledButtonSource.contains("size++"));
        assertTrue(source.contains("setDimWhenInactive(false)"));
        org.junit.jupiter.api.Assertions.assertFalse(panelSource.contains("TabButton"));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/RightToolbarButton.java")));
    }

    @Test
    void inputUsesTwoPageGroupsWithOneSharedCommonPage() throws Exception {
        String paged = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PUnitPagedScreen.java"));
        String input = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java"));
        String manager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PUnitManagerScreen.java"));

        assertTrue(paged.contains("COMMON(\"common\")"));
        assertTrue(paged.contains("if (candidate == Page.COMMON)"));
        assertTrue(paged.contains("addToRightToolbar(\"pageGroup\", pageGroupButton)"));
        assertTrue(paged.contains("ignored -> selectPageGroup(pageGroup == PageGroup.OUTPUT"));
        assertTrue(paged.contains("case ENERGY -> Page.TRANSFER"));
        assertTrue(paged.contains("default -> Page.TRANSFER"));
        assertTrue(input.contains("true, PageGroup.OUTPUT"));
        assertTrue(manager.contains("false, PageGroup.UNIT"));
        assertTrue(manager.contains("syncMain.visible = common"));
    }

    @Test
    void outputUsesOnlyTheOutputPageGroupAndCustomIconsAreRemoved() throws Exception {
        String output = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelOutputScreen.java"));
        assertTrue(output.contains("extends PatternP2PUnitPagedScreen"));
        assertTrue(output.contains("false, PageGroup.OUTPUT"));
        assertTrue(output.contains("supportsUnitPages()"));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/Ae2bcGuiIcon.java")));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/resources/assets/ae2_batchcraft/textures/guis/pattern_p2p_configuration_icons.png")));
    }

    @Test
    void outputPagesExposeSynchronizedExtractionSettingsAndLocalSwitch() throws Exception {
        String screen = readText(OUTPUT_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelOutputScreen.java"));

        assertTrue(screen.contains("\"productExtractionEnabled\""));
        assertTrue(screen.contains("\"productExtractionDisabled\""));
        assertTrue(screen.contains("\"extraction_interval\""));
        assertTrue(screen.contains("\"extraction_amount\""));
        assertTrue(source.contains("productExtractionEnabled.visible = outputCommon"));
        assertTrue(source.contains("productExtractionDisabled.visible = outputCommon"));
        assertTrue(source.contains("extractionControls.setVisible(common)"));
        assertTrue(source.contains("productExtractionEnabled.active = editable && !menu.productExtractionEnabled"));
        assertTrue(source.contains("productExtractionDisabled.active = editable && menu.productExtractionEnabled"));
        assertTrue(source.contains("extractionControls.setEditable(editable)"));
    }

    @Test
    void outputPortConfigProvidesTheAe2PriorityInputStyle() throws Exception {
        String screen = readText(OUTPUT_PORT_CONFIG_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/UnitPortOutputConfigScreen.java"));
        assertTrue(screen.contains("\"priorityInput\""));
        assertTrue(screen.contains("\"priorityInput\": {\"left\": 59, \"top\": 59, "
                + "\"width\": 61, \"height\": 12}"));
        assertTrue(source.contains("new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),"));
        assertTrue(source.contains("menu.singleSlot ? Icon.BLOCKING_MODE_YES : Icon.BLOCKING_MODE_NO"));
        assertTrue(source.contains("setMinValue(PatternP2PUnitPortPart.MIN_TRANSFER_PRIORITY)"));
        assertTrue(source.contains("setMaxValue(PatternP2PUnitPortPart.MAX_TRANSFER_PRIORITY)"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("\"UPGRADE\""));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("AE2_BATCHCRAFT_UNIT_PORT_INVERTER"));
        org.junit.jupiter.api.Assertions.assertTrue(screen.contains("\"top\": 110"));
        assertTrue(screen.contains("\"top\": 158"));
        assertTrue(screen.contains("\"height\": 242"));
    }

    @Test
    void inputPortConfigOnlyProvidesMarkersUpgradesAndPlayerInventory() throws Exception {
        String screen = readText(INPUT_PORT_CONFIG_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/UnitPortInputConfigScreen.java"));

        assertTrue(screen.contains("\"CONFIG\""));
        assertTrue(screen.contains("PLAYER_INVENTORY"));
        assertTrue(screen.contains("PLAYER_HOTBAR"));
        assertTrue(source.contains("new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE)"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("priority"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("singleSlot"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("NumberEntryWidget"));
    }

    private static void assertPagedConfigurationScreen(String resource) throws Exception {
        String screen = readText(resource);

        assertTrue(screen.contains("\"generatedBackground\": {\"width\": 172"), resource);
        assertTrue(screen.contains("\"close\""), resource);
        assertTrue(screen.contains("\"page_title\""), resource);
        assertTrue(screen.contains("\"scale\": 1.2"), resource);
    }

    private static String readText(String resource) throws Exception {
        try (var input = PatternP2PUnitScreenResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
