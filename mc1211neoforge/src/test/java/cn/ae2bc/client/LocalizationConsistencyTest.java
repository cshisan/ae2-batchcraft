package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationConsistencyTest {
    private static final Pattern ENTRY = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");

    @Test
    void localeKeysValuesAndPlaceholderSignaturesStayAligned() throws Exception {
        Map<String, String> english = translations("en_us");
        Map<String, String> chinese = translations("zh_cn");

        assertEquals(english.keySet(), chinese.keySet());
        assertEquals(english.size(), english.values().stream().filter(value -> !value.isBlank()).count());
        assertEquals(chinese.size(), chinese.values().stream().filter(value -> !value.isBlank()).count());
        for (String key : english.keySet()) {
            assertEquals(placeholders(english.get(key)), placeholders(chinese.get(key)), key);
        }
    }

    @Test
    void unitManagerUsesAFullConfigurationSyncDescription() throws Exception {
        Map<String, String> english = translations("en_us");
        Map<String, String> chinese = translations("zh_cn");
        String key = "gui.ae2_batchcraft.pattern_p2p_unit.sync_main_configuration.tooltip";

        assertTrue(english.get(key).contains("redstone"));
        assertTrue(english.get(key).contains("pulse"));
        assertTrue(chinese.get(key).contains("红石"));
        assertTrue(chinese.get(key).contains("脉冲"));
        assertFalse(english.get(key).equals(english.get("gui.ae2_batchcraft.sync_input_settings.tooltip")));
    }

    private static Map<String, String> translations(String locale) throws Exception {
        String resource = "assets/ae2_batchcraft/lang/" + locale + ".json";
        try (var input = LocalizationConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> result = new LinkedHashMap<>();
            var matcher = ENTRY.matcher(json);
            while (matcher.find()) {
                assertFalse(result.containsKey(matcher.group(1)), "duplicate key: " + matcher.group(1));
                result.put(matcher.group(1), matcher.group(2));
            }
            return result;
        }
    }

    private static List<String> placeholders(String value) {
        return PLACEHOLDER.matcher(value).results().map(match -> match.group()).toList();
    }
}
