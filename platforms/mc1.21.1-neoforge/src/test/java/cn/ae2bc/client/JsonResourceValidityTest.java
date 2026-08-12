package cn.ae2bc.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JsonResourceValidityTest {
    @Test
    void everyProductionJsonResourceIsSyntacticallyValid() throws Exception {
        for (Path root : new Path[]{Path.of("src/main/resources"), Path.of("src/generated/resources")}) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                    assertDoesNotThrow(() -> {
                        try (Reader reader = Files.newBufferedReader(path)) {
                            JsonParser.parseReader(reader);
                        }
                    }, path.toString());
                }
            }
        }
    }
}
