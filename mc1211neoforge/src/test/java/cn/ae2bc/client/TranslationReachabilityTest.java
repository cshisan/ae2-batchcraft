package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationReachabilityTest {
    private static final Pattern LOCALE_KEY = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:");
    private static final Pattern MOD_KEY_LITERAL = Pattern.compile(
            "\\\"((?:gui|message|tooltip|item)\\.ae2_batchcraft\\.[^\\\"]+)\\\"");

    @Test
    void everyStaticProductionTranslationReferenceExists() throws Exception {
        Set<String> translations = englishKeys();
        Set<String> references = new HashSet<>();
        for (Path root : new Path[]{Path.of("src/main/java"),
                Path.of("src/main/resources/assets/ae2/screens/ae2_batchcraft")}) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    var matcher = MOD_KEY_LITERAL.matcher(Files.readString(path));
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        if (!key.endsWith(".") && !key.endsWith("_")) {
                            references.add(key);
                        }
                    }
                }
            }
        }

        for (String key : references) {
            assertTrue(translations.contains(key), key);
        }
    }

    @Test
    void everyDynamicTranslationFamilyIsComplete() throws Exception {
        Set<String> keys = englishKeys();

        assertFamily(keys, "gui.ae2_batchcraft.direction.",
                "auto", "down", "up", "north", "south", "west", "east", "left", "right");
        assertFamily(keys, "gui.ae2_batchcraft.component_placer.direction.",
                "down", "up", "north", "south", "west", "east");
        assertFamily(keys, "gui.ae2_batchcraft.component_placer.relative.", "left", "right");
        assertFamily(keys, "gui.ae2_batchcraft.energy_distribution_mode.", "even", "round_robin");
        assertFamily(keys, "gui.ae2_batchcraft.material_output_form.", "normal", "drop", "place");
        assertFamily(keys, "gui.ae2_batchcraft.pattern_p2p_unit.page.", "common", "break", "redstone", "switch");
        assertFamily(keys, "gui.ae2_batchcraft.pattern_p2p_unit.redstone_mode.",
                "single_trigger", "periodic_pulse", "continuous");
        assertFamily(keys, "gui.ae2_batchcraft.pattern_p2p_unit.return_mode.", "strict", "unblocked");
        assertFamily(keys, "gui.ae2_batchcraft.return_mode.",
                "strict", "strict.tooltip", "unblocked", "unblocked.tooltip");
        assertFamily(keys, "gui.ae2_batchcraft.component_placer.selection.",
                "valid", "incomplete", "volume", "too_large");
        assertFamily(keys, "message.ae2_batchcraft.component_placer.selection_",
                "volume_not_allowed", "too_large");
    }

    private static Set<String> englishKeys() throws Exception {
        String json = Files.readString(Path.of(
                "src/main/resources/assets/ae2_batchcraft/lang/en_us.json"));
        Set<String> result = new HashSet<>();
        var matcher = LOCALE_KEY.matcher(json);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static void assertFamily(Set<String> keys, String prefix, String... suffixes) {
        for (String suffix : suffixes) {
            assertTrue(keys.contains(prefix + suffix), prefix + suffix);
        }
    }
}
