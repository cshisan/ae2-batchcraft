package cn.ae2bc.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ae2DependencyMetadataTest {
    @Test
    void supportsTheVerifiedAe2FifteenFourApiLine() throws Exception {
        String metadata;
        try (var input = getClass().getClassLoader().getResourceAsStream("META-INF/mods.toml")) {
            if (input == null) {
                throw new AssertionError("META-INF/mods.toml");
            }
            metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(metadata.contains("modId=\"ae2\""));
        assertTrue(metadata.contains("versionRange=\"[15.4.10,16.0.0)\""));

        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("15.4.10", properties.getProperty("ae2_version"));
    }
}
