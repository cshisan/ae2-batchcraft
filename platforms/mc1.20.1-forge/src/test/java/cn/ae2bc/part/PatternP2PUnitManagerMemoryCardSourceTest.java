package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitManagerMemoryCardSourceTest {
    @Test
    void customMemoryCardHandlingBypassesAe2StandardInterceptor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitManagerPart.java"));

        int methodStart = source.indexOf("public boolean useStandardMemoryCard()");
        int methodEnd = source.indexOf("public boolean onPartActivate", methodStart);
        assertTrue(methodStart >= 0 && source.substring(methodStart, methodEnd).contains("return false;"),
                "AEBasePart intercepts memory cards before onPartActivate unless standard handling is disabled");
    }
}
