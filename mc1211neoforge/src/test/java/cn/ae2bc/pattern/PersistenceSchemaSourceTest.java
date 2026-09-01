package cn.ae2bc.pattern;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceSchemaSourceTest {
    @Test
    void productionSourcesOnlyUseTheCanonicalMaterialOutputFormat() throws Exception {
        String sources = readProductionJavaSources();

        assertTrue(sources.contains("material_output_config"));
        assertTrue(sources.contains("MaterialOutputConfig"));
        assertFalse(sources.contains("input_directions"));
        assertFalse(sources.contains("InputDirections"));
        assertFalse(sources.contains("INPUT_DIRECTIONS"));
        assertFalse(sources.contains("fromLegacy"));
    }

    @Test
    void unitManagerFrequencyUsesTheCanonicalModOwnedKey() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitManagerPart.java"));

        assertTrue(source.contains("\"PatternP2PFrequency\""));
        assertFalse(source.contains("\"freq\""));
    }

    private static String readProductionJavaSources() throws Exception {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }
}
