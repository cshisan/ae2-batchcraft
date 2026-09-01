package cn.ae2bc.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PTooltipResourceTest {
    @Test
    void registersTheAe2InGameTooltipProvider() throws Exception {
        String service = readText(
                "META-INF/services/appeng.api.integrations.igtooltip.TooltipProvider").trim();
        assertEquals("cn.ae2bc.integration.PatternP2PTooltipProvider", service);
        assertEquals(service, cn.ae2bc.integration.PatternP2PTooltipProvider.class.getName());
    }

    @Test
    void bothLanguagesDescribeDestructiveResetConfirmationAndFrequencies() throws Exception {
        for (String language : new String[]{"en_us", "zh_cn"}) {
            String text = readText("assets/ae2_batchcraft/lang/" + language + ".json");
            assertTrue(text.contains("gui.ae2_batchcraft.reset_task.confirm.input"));
            assertTrue(text.contains("tooltip.ae2_batchcraft.p2p_frequency"));
            assertTrue(text.contains("tooltip.ae2_batchcraft.unit_frequency"));
            assertTrue(text.contains("tooltip.ae2_batchcraft.task_state"));
            assertFalse(text.contains("tooltip.ae2_batchcraft.task_state.active_count"));
            assertTrue(text.contains("tooltip.ae2_batchcraft.pattern_batch_count"));
        }
    }

    private static String readText(String resource) throws Exception {
        try (var input = PatternP2PTooltipResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
