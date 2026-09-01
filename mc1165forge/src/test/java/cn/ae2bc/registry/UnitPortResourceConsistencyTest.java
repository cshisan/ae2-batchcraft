package cn.ae2bc.registry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;

public final class UnitPortResourceConsistencyTest {
    private static final Path RESOURCES = Paths.get("src/main/resources");

    @Test
    public void collectPortHasTheCurrentIdModelTextureAndTranslations() throws Exception {
        assertTrue(Files.isRegularFile(asset(
                "models/item/pattern_p2p_unit_port_collect.json")));
        assertTrue(Files.isRegularFile(asset(
                "models/part/p2p/pattern_p2p_unit_port_collect.json")));
        assertTrue(Files.isRegularFile(asset("textures/part/p2p/unit_port_collect.png")));

        String english = read(asset("lang/en_us.json"));
        String chinese = read(asset("lang/zh_cn.json"));
        assertTrue(english.contains(
                "\"item.ae2_batchcraft.pattern_p2p_unit_port_collect\""));
        assertTrue(chinese.contains(
                "\"item.ae2_batchcraft.pattern_p2p_unit_port_collect\""));
    }

    @Test
    public void productionResourcesContainNoDeprecatedPickupIdentity() throws Exception {
        try (Stream<Path> paths = Files.walk(RESOURCES)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                String normalized = path.toString().replace('\\', '/');
                assertFalse(normalized, normalized.contains("pickup"));
                if (isTextResource(path)) {
                    assertFalse(normalized, read(path).contains("pickup"));
                }
            }
        }
    }

    private static Path asset(String relative) {
        return RESOURCES.resolve("assets/ae2_batchcraft").resolve(relative);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static boolean isTextResource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") || name.endsWith(".lang")
                || name.endsWith(".cfg") || name.endsWith(".toml");
    }
}
