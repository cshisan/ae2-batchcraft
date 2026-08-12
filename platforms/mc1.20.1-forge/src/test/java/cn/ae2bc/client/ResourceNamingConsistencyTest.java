package cn.ae2bc.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceNamingConsistencyTest {
    private static final Set<String> COLORS = Set.of(
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
            "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow");

    @Test
    void productionResourcesContainNoDeprecatedUnitPrefix() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/resources"))) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(ResourceNamingConsistencyTest::isTextResource).toList()) {
                assertFalse(path.toString().contains("pp2p_unit"), path.toString());
                String content = Files.readString(path);
                assertFalse(content.contains("pp2p_unit"), path.toString());
            }
        }
    }

    @Test
    void coloredManagerRecipesMatchTheirResultIds() throws Exception {
        Path recipes = Path.of("src/main/resources/data/ae2_batchcraft/recipes");
        Set<String> actual;
        try (var paths = Files.list(recipes)) {
            actual = paths.map(path -> path.getFileName().toString())
                    .filter(name -> COLORS.stream().anyMatch(color ->
                            name.equals(color + "_pattern_p2p_unit_manager.json")))
                    .collect(Collectors.toSet());
        }
        Set<String> expected = COLORS.stream()
                .map(color -> color + "_pattern_p2p_unit_manager.json")
                .collect(Collectors.toSet());
        assertEquals(expected, actual);

        for (String color : COLORS) {
            String name = color + "_pattern_p2p_unit_manager.json";
            var recipe = JsonParser.parseString(Files.readString(recipes.resolve(name))).getAsJsonObject();
            assertEquals("ae2_batchcraft:" + color + "_pattern_p2p_unit_manager",
                    recipe.getAsJsonObject("result").get("item").getAsString(), name);
        }
        assertTrue(Files.exists(recipes.resolve("pattern_p2p_unit_manager_remove_color.json")));
    }

    private static boolean isTextResource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") || name.endsWith(".md") || name.endsWith(".toml");
    }
}
