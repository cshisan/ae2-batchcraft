package cn.ae2bc.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public final class EnglishGuiLayoutTest {
    @Test
    public void compactButtonLabelsFit() throws Exception {
        Map<String, String> english = english();
        assertEquals("Down", english.get("gui.ae2_batchcraft.component_placer.direction.down"));
        assertEquals("Up", english.get("gui.ae2_batchcraft.component_placer.direction.up"));
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.single_trigger", 44);
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.periodic_pulse", 44);
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.continuous", 44);
    }

    private static void assertFits(Map<String, String> english, String key, int availableWidth) {
        assertTrue(key, width(english.get(key)) <= availableWidth);
    }

    private static int width(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result += c == ' ' ? 4 : "!,.\u003a;il|".indexOf(c) >= 0 ? 2 : 6;
        }
        return result;
    }

    private static Map<String, String> english() throws Exception {
        InputStream input = EnglishGuiLayoutTest.class.getResourceAsStream(
                "/assets/ae2_batchcraft/lang/en_us.lang");
        if (input == null) throw new AssertionError("Missing English language resource");
        Map<String, String> result = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            int separator = line.indexOf('=');
            if (separator > 0) result.put(line.substring(0, separator), line.substring(separator + 1));
        }
        reader.close();
        return result;
    }
}
