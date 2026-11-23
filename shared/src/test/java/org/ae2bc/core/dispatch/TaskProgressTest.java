package org.ae2bc.core.dispatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class TaskProgressTest {
    @Test
    void recordingExcessNeverShrinksTheDeclaredReturnScope() {
        TaskProgress<String> progress = new TaskProgress<>();
        Map<String, Long> outputs = new LinkedHashMap<>();
        outputs.put("primary", 6L);
        outputs.put("secondary", 8L);
        progress.begin(outputs);
        Assertions.assertFalse(progress.record("unrelated", 64));
        Assertions.assertTrue(progress.record("primary", 64));
        Assertions.assertEquals(outputs, progress.declared());
        Assertions.assertTrue(progress.pending(null));
        Assertions.assertFalse(progress.pending("primary"));
        Assertions.assertFalse(progress.record("primary", 1));
        Assertions.assertTrue(progress.record("secondary", 8));
        Assertions.assertFalse(progress.pending(null));
    }

    @Test
    void aNewTaskOwnsAnIndependentSnapshotAndClearsOldCompletionState() {
        TaskProgress<String> progress = new TaskProgress<>();
        Map<String, Long> outputs = new LinkedHashMap<>();
        outputs.put("old", 1L);
        progress.begin(outputs);
        outputs.clear();
        Assertions.assertTrue(progress.pending(null));
        progress.begin(Collections.singletonMap("new", Long.MAX_VALUE));
        Assertions.assertEquals(Collections.singletonMap("new", Long.MAX_VALUE), progress.declared());
        Assertions.assertTrue(progress.record("new", Long.MAX_VALUE));
        Assertions.assertFalse(progress.pending(null));
        progress.clear();
        Assertions.assertTrue(progress.declared().isEmpty());
        Assertions.assertTrue(progress.remaining().isEmpty());
    }
}
