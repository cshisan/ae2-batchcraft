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

    @Test
    void configurationScreensExposeTheSharedPageNavigation() throws Exception {
        assertPagedConfigurationScreen(MANAGER_SCREEN);
        assertPagedConfigurationScreen(INPUT_SCREEN);
    }

    @Test
    void taskEndpointsExposeTheResetTaskButton() throws Exception {
        assertTrue(readText(MANAGER_SCREEN).contains("\"rightToolbar\""));
        assertTrue(readText(INPUT_SCREEN).contains("\"rightToolbar\""));
        assertTrue(readText(OUTPUT_SCREEN).contains("\"resetTask\""));
    }

    @Test
    void inputCommonPageContainsExtractionControlsWithoutFilterSlots() throws Exception {
        String screen = readText(INPUT_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java"));

        assertTrue(screen.contains("\"productExtraction\""));
        assertTrue(screen.contains("\"extraction_interval\""));
        assertTrue(screen.contains("\"extraction_amount\""));
        assertTrue(source.contains("product_extraction.enabled\"), false"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("product_extraction.enable\""));
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
    void extractionFieldsShareTheSameLabelAwareHorizontalPosition() throws Exception {
        String controls = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/ProductExtractionControls.java"));
        assertTrue(controls.contains("int inputX = left + 16 + Math.max("));
        assertTrue(controls.contains("inputX, top + 92"));
        assertTrue(controls.contains("inputX, top + 113"));
        for (String sourcePath : new String[]{
                "src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java",
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
        assertTrue(source.contains("case TRANSFER -> Icon.ACCESS_WRITE"));
        assertTrue(source.contains("case REDSTONE -> Icon.REDSTONE_ON"));
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
        String pagedSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/PatternP2PUnitPagedScreen.java"));
        assertTrue(pagedSource.contains("case TRANSFER -> 1.1f"));
        assertTrue(pagedSource.contains("case BREAK -> 0.9f"));
        assertTrue(pagedSource.contains("page == Page.TRANSFER ? 1 : 0"));
        assertTrue(pagedSource.contains("setDimWhenInactive(false)"));
        org.junit.jupiter.api.Assertions.assertFalse(panelSource.contains("TabButton"));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/RightToolbarButton.java")));
    }

    @Test
    void outputPortConfigProvidesTheAe2PriorityInputStyle() throws Exception {
        String screen = readText(OUTPUT_PORT_CONFIG_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/UnitPortOutputConfigScreen.java"));
        assertTrue(screen.contains("\"priorityInput\""));
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

    private static void assertPagedConfigurationScreen(String resource) throws Exception {
        String screen = readText(resource);

        assertTrue(screen.contains("\"generatedBackground\": {\"width\": 176"), resource);
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
