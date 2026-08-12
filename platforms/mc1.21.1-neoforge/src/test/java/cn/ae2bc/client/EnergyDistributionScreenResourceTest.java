package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyDistributionScreenResourceTest {
    @Test
    void energyTunnelAndUnitManagerExposeGlobalEnergyDistributionMode() throws Exception {
        String energy = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_energy.json");
        String output = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_output.json");
        String unit = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_unit_manager.json");

        assertTrue(energy.contains("\"energyDistributionMode\""));
        assertTrue(energy.contains("\"top\": 77"));
        org.junit.jupiter.api.Assertions.assertFalse(output.contains("\"energyDistributionMode\""));
        assertTrue(unit.contains("\"energyDistributionMode\""));
    }

    @Test
    void unitManagerCommonSettingsHaveVisibleVerticalSpacing() throws Exception {
        String unit = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_unit_manager.json");

        assertTrue(unit.contains("\"syncMain\": {\"left\": 106, \"top\": 21"));
        assertTrue(unit.contains("\"returnStrict\": {\"left\": 12, \"top\": 50"));
        assertTrue(unit.contains("\"extraction_interval\""));
        assertTrue(unit.contains("\"extraction_amount\""));
        assertTrue(unit.contains("\"energyDistributionMode\": {\"left\": 12, \"top\": 153"));
        assertTrue(unit.contains("\"resetTask\": {\"left\": 12, \"top\": 195"));
    }

    @Test
    void relatedConfigurationScreensUseCenteredDashedSectionControls() throws Exception {
        String input = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json");
        String energy = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_energy.json");
        String output = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_output.json");

        assertTrue(input.contains("\"returnStrict\": {\"left\": 12, \"top\": 50"));
        assertTrue(input.contains("\"productExtraction\": {\"left\": 128, \"top\": 79"));
        assertTrue(input.contains("\"extraction_interval\""));
        assertTrue(input.contains("\"extraction_amount\""));
        assertTrue(input.contains("\"resetTask\": {\"left\": 12, \"top\": 154"));
        String inputSource = resourceSource("src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java");
        assertTrue(inputSource.contains(
                "DashedSectionRenderer.trailingContentX(imageWidth, productExtractionWidth)"));
        assertTrue(energy.contains("\"left\": 12"));
        assertTrue(energy.contains("\"top\": 77"));
        assertTrue(energy.contains("\"width\": 176"));
        org.junit.jupiter.api.Assertions.assertFalse(energy.contains("\"mode\""));
        org.junit.jupiter.api.Assertions.assertFalse(energy.contains("\"interval\""));
        assertTrue(output.contains("\"syncInputSettings\": {\"left\": 8, \"top\": 22"));
        assertTrue(output.contains("\"returnStrict\": {\"left\": 12, \"top\": 50"));
        assertTrue(output.contains("\"resetTask\": {\"left\": 12, \"top\": 92"));
    }

    @Test
    void breakRecoveryCheckboxesUseCompactLabelFirstLayout() throws Exception {
        String input = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json");
        String unit = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_unit_manager.json");

        assertTrue(input.contains("\"breakRecovery\": {\"left\": 12, \"top\": 52"));
        assertTrue(unit.contains("\"breakRecovery\": {\"left\": 12, \"top\": 52"));
        org.junit.jupiter.api.Assertions.assertFalse(input.contains("\"break_recovery\":"));
        org.junit.jupiter.api.Assertions.assertFalse(unit.contains("\"break_recovery\":"));
    }

    @Test
    void redstonePagesUseSegmentedModesAndAlignedSignalFields() throws Exception {
        String input = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_tunnel_input.json");
        String unit = resource("assets/ae2/screens/ae2_batchcraft/pattern_p2p_unit_manager.json");

        for (String screen : new String[]{input, unit}) {
            assertTrue(screen.contains("\"redstoneSingle\": {\"left\": 12, \"top\": 50"));
            assertTrue(screen.contains("\"redstonePeriodic\": {\"left\": 64, \"top\": 50"));
            assertTrue(screen.contains("\"redstoneContinuous\": {\"left\": 116, \"top\": 50"));
            assertTrue(screen.contains("\"strength\""));
            assertTrue(screen.contains("\"top\": 96"));
            assertTrue(screen.contains("\"left\": 143, \"top\": 117"));
            assertTrue(screen.contains("\"left\": 143, \"top\": 138"));
            org.junit.jupiter.api.Assertions.assertFalse(screen.contains("\"redstoneMode\""));
        }

        String inputSource = resourceSource("src/main/java/cn/ae2bc/client/PatternP2PTunnelInputScreen.java");
        String managerSource = resourceSource("src/main/java/cn/ae2bc/client/PatternP2PUnitManagerScreen.java");
        for (String source : new String[]{inputSource, managerSource}) {
            assertTrue(source.contains("leftPos + 104, topPos + 92"));
            assertTrue(source.contains("leftPos + 104, topPos + 113"));
            assertTrue(source.contains("leftPos + 104, topPos + 134"));
        }
    }

    @Test
    void translationsContainRequestedChineseLabelsAndBothModes() throws Exception {
        String chinese = resource("assets/ae2_batchcraft/lang/zh_cn.json");
        String english = resource("assets/ae2_batchcraft/lang/en_us.json");

        assertTrue(chinese.contains("传电方式"));
        assertTrue(chinese.contains("均分"));
        assertTrue(chinese.contains("轮询"));
        assertTrue(chinese.contains("间隔：%s 刻"));
        assertTrue(chinese.contains("重置可能销毁未下发材料,请确保材料已完整输出"));
        assertTrue(chinese.contains("能量配置"));
        assertTrue(chinese.contains("任务重置"));
        assertTrue(chinese.contains("严格模式"));
        assertTrue(english.contains("energy_distribution_mode.even"));
        assertTrue(english.contains("energy_distribution_mode.round_robin"));
    }

    private static String resource(String name) throws Exception {
        try (var input = EnergyDistributionScreenResourceTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new AssertionError("Missing resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String resourceSource(String name) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(name));
    }
}
