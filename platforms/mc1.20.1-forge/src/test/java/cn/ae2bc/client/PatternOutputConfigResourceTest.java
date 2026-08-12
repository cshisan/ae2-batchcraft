package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternOutputConfigResourceTest {
    @Test
    void outputConfigurationScreenHasDynamicValueLabels() throws Exception {
        String screen = readText("assets/ae2/screens/ae2_batchcraft/material_output_config.json");
        assertTrue(screen.contains("\"output_direction\""));
        assertTrue(screen.contains("\"output_form\""));

        for (String language : new String[]{"en_us", "zh_cn"}) {
            String translations = readText("assets/ae2_batchcraft/lang/" + language + ".json");
            assertTrue(translations.contains("gui.ae2_batchcraft.output_direction_value"));
            assertTrue(translations.contains("gui.ae2_batchcraft.material_output_form_value"));
        }
    }

    @Test
    void encodedPatternsRegisterCompactOutputConfigurationTooltip() throws Exception {
        String mixins = readText("ae2_batchcraft.mixins.json");
        assertTrue(mixins.contains("EncodedPatternItemMixin"));

        for (String language : new String[]{"en_us", "zh_cn"}) {
            String translations = readText("assets/ae2_batchcraft/lang/" + language + ".json");
            assertTrue(translations.contains("tooltip.ae2_batchcraft.material_output_config"));
            assertTrue(translations.contains("tooltip.ae2_batchcraft.material_output_config.entry"));
        }
    }

    private static String readText(String resource) throws Exception {
        try (var input = PatternOutputConfigResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
