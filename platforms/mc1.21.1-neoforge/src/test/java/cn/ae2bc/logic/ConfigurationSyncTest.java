package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSyncTest {
    @Test
    void lowerRevisionFromAReplacementSourceIsAccepted() {
        assertTrue(ConfigurationSync.shouldApply("old", 42, "new", 0));
    }

    @Test
    void identicalConfigurationAndRevisionIsIgnored() {
        assertFalse(ConfigurationSync.shouldApply("same", 7, "same", 7));
    }

    @Test
    void changedConfigurationAtTheSameRevisionIsAccepted() {
        assertTrue(ConfigurationSync.shouldApply("old", 7, "new", 7));
    }
}
