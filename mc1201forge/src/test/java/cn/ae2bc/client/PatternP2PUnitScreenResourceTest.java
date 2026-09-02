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
        assertTrue(readText(MANAGER_SCREEN).contains("\"resetTaskToolbar\""));
        assertTrue(readText(INPUT_SCREEN).contains("\"resetTaskToolbar\""));
        assertTrue(readText(OUTPUT_SCREEN).contains("\"resetTask\""));
    }

    @Test
    void inputScreenExposesTheTaskAllocationToolbarButton() throws Exception {
        String input = readText(INPUT_SCREEN);
        assertTrue(input.contains("\"taskAllocationModeToolbar\": {\"left\": 176, \"top\": 41"));
    }

    @Test
    void taskAllocationToolbarDoesNotUseFocusedSelectionBackground() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/ScaledTabButton.java"));
        assertTrue(source.contains("background = Icon.HORIZONTAL_TAB;"));
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

        assertTrue(source.contains("style.getWidget(\"verticalToolbar\").getTop()"));
        assertTrue(source.contains("case TRANSFER -> Icon.ACCESS_WRITE"));
        assertTrue(source.contains("case REDSTONE -> Icon.REDSTONE_HIGH"));
    }

    @Test
    void outputPortConfigProvidesTheAe2PriorityInputStyle() throws Exception {
        String screen = readText(OUTPUT_PORT_CONFIG_SCREEN);
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/ae2bc/client/UnitPortOutputConfigScreen.java"));
        assertTrue(screen.contains("\"priorityInput\""));
        assertTrue(source.contains("new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),"));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("\"UPGRADE\""));
        org.junit.jupiter.api.Assertions.assertFalse(screen.contains("AE2_BATCHCRAFT_UNIT_PORT_INVERTER"));
        org.junit.jupiter.api.Assertions.assertTrue(screen.contains("\"top\": 110"));
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
