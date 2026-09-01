package cn.ae2bc.client;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class EnglishGuiLayoutTest {
    @Test
    public void compactButtonLabelsFit() throws Exception {
        JsonObject english = english();
        assertTrue("Down".equals(value(english, "gui.ae2_batchcraft.component_placer.direction.down")));
        assertTrue("Up".equals(value(english, "gui.ae2_batchcraft.component_placer.direction.up")));
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.single_trigger", 44);
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.periodic_pulse", 44);
        assertFits(english, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.continuous", 44);
    }

    private static void assertFits(JsonObject english, String key, int availableWidth) {
        assertTrue(key, width(value(english, key)) <= availableWidth);
    }

    private static String value(JsonObject english, String key) {
        return english.get(key).getAsString();
    }

    private static int width(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result += c == ' ' ? 4 : "!,.\u003a;il|".indexOf(c) >= 0 ? 2 : 6;
        }
        return result;
    }

    private static JsonObject english() throws Exception {
        InputStream input = EnglishGuiLayoutTest.class.getResourceAsStream(
                "/assets/ae2_batchcraft/lang/en_us.json");
        if (input == null) throw new AssertionError("Missing English language resource");
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        JsonObject result = new JsonParser().parse(reader).getAsJsonObject();
        reader.close();
        return result;
    }
}
