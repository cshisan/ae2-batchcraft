package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class ExtractionRecoverySourceTest {
    @Test
    public void failedSourceRestoreIsPersistedAndDroppedWithThePart() throws Exception {
        String tunnel = readPart("PatternP2PTunnelPart.java");
        String port = readPart("PatternP2PUnitPortPart.java");
        String queue = readLogic("ItemStackExtractionRecoveryQueue.java");

        assertTrue(tunnel.contains("extractionRecovery.queue(ItemHandlerHelper.insertItem(source"));
        assertTrue(port.contains("extractionRecovery.queue(ItemHandlerHelper.insertItem(source"));
        assertTrue(tunnel.contains("extractionRecovery.read(tag, EXTRACTION_RECOVERY)"));
        assertTrue(tunnel.contains("extractionRecovery.write(tag, EXTRACTION_RECOVERY)"));
        assertTrue(port.contains("extractionRecovery.read(data, \"ProductExtractionRecovery\")"));
        assertTrue(port.contains("extractionRecovery.write(data, \"ProductExtractionRecovery\")"));
        assertTrue(count(tunnel, "extractionRecovery.addDrops(drops);") == 1);
        assertTrue(count(port, "extractionRecovery.addDrops(drops);") == 1);
        assertTrue(queue.contains("new LinkedHashMap<StackKey, Long>()"));
        assertFalse(queue.contains("RecoveryBuffer"));
        assertTrue(queue.contains("entry.setLong(\"Amount\""));
        assertTrue(queue.contains("changeListener.run();"));
    }

    @Test
    public void recoveryCanDrainAfterTaskEndButNewExtractionCannotStart() throws Exception {
        String tunnel = readPart("PatternP2PTunnelPart.java");
        String port = readPart("PatternP2PUnitPortPart.java");
        String manager = readPart("PatternP2PUnitManagerPart.java");

        String outputTick = section(tunnel, "public TickRateModulation tickingRequest", "@Override public void readFromNBT");
        assertTrue(outputTick.indexOf("drainExtractionRecovery()")
                < outputTick.indexOf("!returnBatch.isActive()"));
        assertTrue(outputTick.contains("!returnBatch.isActive() || !productExtractionSettings.isEnabled()"));

        String portTick = section(port, "TickRateModulation tickingRequest", "private TickRateModulation tickRedstone");
        assertTrue(portTick.indexOf("drainExtractionRecovery(manager)")
                < portTick.indexOf("!manager.isTaskActive()"));
        assertTrue(port.contains("moved < amount && manager.isTaskActive()"));

        String recovery = section(manager, "public ItemStack returnProductRecovery", "private PatternP2PTunnelPart findInput");
        assertTrue(recovery.contains("input.returnToAdjacent(stack, false)"));
        assertFalse(recovery.contains("isTaskActive()"));
    }

    private static String readPart(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/part", name)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String readLogic(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/logic", name)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing section start: " + start, from >= 0);
        assertTrue("Missing section end: " + end, to > from);
        return source.substring(from, to);
    }

    private static int count(String source, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
