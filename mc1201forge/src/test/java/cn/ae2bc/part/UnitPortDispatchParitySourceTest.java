package cn.ae2bc.part;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Guards the shared admission path used by drop, place, and transfer ports. */
class UnitPortDispatchParitySourceTest {
    @Test
    void inputAdmissionUsesUnifiedFilter() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));
        assertTrue(source.contains("if (!matchesInput(manager, stack, form))"));
        assertTrue(source.contains("this::wakeForTargetChange"));
    }

    @Test
    void targetChangesNotifyManagerRetry() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));
        assertTrue(source.contains("manager.getLogic().alertPendingRetry()"));
        assertTrue(source.contains("wakeForTargetChange();"));
    }
}
