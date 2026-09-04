package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSyncStateTest {
    @Test
    void disabledSynchronizationKeepsLocalValueAndIgnoresBroadcasts() {
        ConfigurationSync.State<String> state = new ConfigurationSync.State<>("local", false, -1);

        assertFalse(state.applyBroadcast("remote", 1));
        assertEquals("local", state.value());
        assertEquals(-1, state.lastAppliedRevision());

        assertTrue(state.setLocalValue("edited"));
        assertEquals("edited", state.value());
    }

    @Test
    void enabledSynchronizationAppliesBroadcastAndTracksRevision() {
        ConfigurationSync.State<String> state = new ConfigurationSync.State<>("local", true, -1);

        assertTrue(state.applyBroadcast("remote", 7));
        assertEquals("remote", state.value());
        assertEquals(7, state.lastAppliedRevision());
        assertFalse(state.applyBroadcast("remote", 7));
    }

    @Test
    void disablingSynchronizationDoesNotRestoreAnOlderLocalValue() {
        ConfigurationSync.State<String> state = new ConfigurationSync.State<>("local", true, -1);

        assertTrue(state.applyBroadcast("remote", 2));
        assertTrue(state.setSynchronizationEnabled(false));
        assertEquals("remote", state.value());
        assertFalse(state.applyBroadcast("new-remote", 3));
        assertEquals("remote", state.value());
    }
}
