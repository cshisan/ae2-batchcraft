package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentPlacerResourceTest {
    private static final String SCREEN = "assets/ae2/screens/ae2_batchcraft/component_placer.json";
    private static final String CHINESE = "assets/ae2_batchcraft/lang/zh_cn.json";
    private static final String ENGLISH = "assets/ae2_batchcraft/lang/en_us.json";

    @Test
    void linkStatusUsesModTranslationsAvailableInBothLanguages() throws Exception {
        String screen = readText(SCREEN);

        assertTrue(screen.contains("gui.ae2_batchcraft.component_placer.linked"));
        assertTrue(screen.contains("gui.ae2_batchcraft.component_placer.not_linked"));
        assertTrue(screen.contains("\"height\": 228"));
        assertTrue(screen.contains("\"left\": 40"));
        assertTrue(screen.contains("\"left\": 88"));
        assertTranslations(CHINESE);
        assertTranslations(ENGLISH);
    }

    private static void assertTranslations(String resource) throws Exception {
        String translations = readText(resource);

        assertTrue(translations.contains("\"gui.ae2_batchcraft.component_placer.linked\""), resource);
        assertTrue(translations.contains("\"gui.ae2_batchcraft.component_placer.not_linked\""), resource);
    }

    private static String readText(String resource) throws Exception {
        try (var input = ComponentPlacerResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
