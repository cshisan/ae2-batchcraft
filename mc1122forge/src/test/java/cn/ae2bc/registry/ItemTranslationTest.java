package cn.ae2bc.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public final class ItemTranslationTest {
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final String[] PORTS = {
            "drop", "collect", "place", "break", "transfer", "return", "extract", "redstone",
            "energy"
    };

    @Test
    public void englishAndChineseCoverEveryRegisteredItemName() throws Exception {
        Set<String> expected = expectedKeys();
        assertEquals(30, expected.size());
        assertEquals(expected, itemKeys("en_us"));
        assertEquals(expected, itemKeys("zh_cn"));
    }

    @Test
    public void resourcePackUsesMinecraft112Format() throws Exception {
        InputStream input = ItemTranslationTest.class.getResourceAsStream("/pack.mcmeta");
        assertNotNull("Missing pack.mcmeta", input);
        try {
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(3, root.getAsJsonObject("pack").get("pack_format").getAsInt());
        } finally {
            input.close();
        }
    }

    private static Set<String> expectedKeys() {
        Set<String> keys = new LinkedHashSet<String>();
        keys.add(key("pattern_p2p_tunnel_input"));
        keys.add(key("pattern_p2p_tunnel_output"));
        keys.add(key("pattern_p2p_tunnel_energy"));
        keys.add(key("pattern_p2p_unit_manager"));
        keys.add(key("component_placer"));
        for (String port : PORTS) keys.add(key("pattern_p2p_unit_port_" + port));
        for (String color : COLORS) keys.add(key(color + "_pattern_p2p_unit_manager"));
        return keys;
    }

    private static String key(String id) {
        return "item.ae2_batchcraft." + id + ".name";
    }

    private static Set<String> itemKeys(String language) throws IOException {
        String resource = "/assets/ae2_batchcraft/lang/" + language + ".lang";
        InputStream input = ItemTranslationTest.class.getResourceAsStream(resource);
        assertNotNull("Missing language resource " + resource, input);
        Set<String> keys = new HashSet<String>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("item.")) {
                    int separator = line.indexOf('=');
                    assertTrue("Malformed language entry: " + line, separator > 0);
                    String key = line.substring(0, separator);
                    assertFalse("Invalid duplicated name suffix: " + key, key.endsWith(".name.name"));
                    keys.add(key);
                }
            }
        } finally {
            input.close();
        }
        return keys;
    }
}
