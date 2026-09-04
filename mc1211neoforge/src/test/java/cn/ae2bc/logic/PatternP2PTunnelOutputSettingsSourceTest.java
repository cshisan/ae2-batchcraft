package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PTunnelOutputSettingsSourceTest {
    @Test
    void inputSynchronizationIncludesAllOutputExtractionSettings() throws Exception {
        String input = source("logic/PatternP2PTunnelInputLogic.java");
        String output = source("logic/PatternP2PTunnelOutputLogic.java");

        assertTrue(input.contains("applyInputSettings(returnMode, productExtractionSettings)"));
        assertTrue(output.contains("applyInputSettings(ReturnMode mode,"
                + " EndpointProductExtractionSettings extractionSettings)"));
        assertTrue(output.contains("configurationState.applyBroadcast(incoming, extractionSettings.revision())"));
        assertTrue(output.contains("extractionSettings.enabled(), extractionSettings.interval(),"
                + " extractionSettings.amount()"));
    }

    @Test
    void outputKeepsPersistentLocalExtractionSettings() throws Exception {
        String output = source("logic/PatternP2PTunnelOutputLogic.java");

        assertTrue(output.contains("PRODUCT_EXTRACTION_ENABLED = \"ProductExtractionEnabled\""));
        assertTrue(output.contains("PRODUCT_EXTRACTION_INTERVAL = \"ProductExtractionInterval\""));
        assertTrue(output.contains("PRODUCT_EXTRACTION_AMOUNT = \"ProductExtractionAmount\""));
        assertTrue(output.contains("data.putBoolean(PRODUCT_EXTRACTION_ENABLED, extractionSettings.enabled())"));
        assertTrue(output.contains("return configurationState.value().productExtractionSettings()"));
    }

    @Test
    void outputMenuRejectsLocalEditsWhileSynchronized() throws Exception {
        String menu = source("menu/PatternP2PTunnelOutputMenu.java");

        assertTrue(menu.contains("@GuiSync(2) public boolean productExtractionEnabled"));
        assertTrue(menu.contains("@GuiSync(3) public int productExtractionInterval"));
        assertTrue(menu.contains("@GuiSync(4) public int productExtractionAmount"));
        assertTrue(menu.contains("!host.getOutputLogic().isSyncInputSettings()"));
        assertTrue(menu.contains("setProductExtractionEnabled(enabled)"));
        assertTrue(menu.contains("setProductExtractionInterval(interval)"));
        assertTrue(menu.contains("setProductExtractionAmount(amount)"));
    }

    @Test
    void inputSwitchOnlyExtractionEditsAreBroadcast() throws Exception {
        String input = source("logic/PatternP2PTunnelInputLogic.java");
        String update = input.substring(input.indexOf("private void updateProductExtraction"),
                input.indexOf("public void setPatternP2PUnitConfiguration"));

        assertTrue(update.contains("productExtractionSettings = updated;"));
        assertTrue(update.contains("synchronizeSettings();"));
        assertTrue(update.contains("The enabled flag is not part of PatternP2PUnitConfiguration"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc/", relativePath));
    }
}
