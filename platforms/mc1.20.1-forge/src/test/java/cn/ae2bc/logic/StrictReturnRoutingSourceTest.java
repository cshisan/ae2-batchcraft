package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictReturnRoutingSourceTest {
    @Test
    void standardOutputCapabilitiesAndActiveExtractionUseTheTaskFilter() throws Exception {
        String part = read("src/main/java/cn/ae2bc/part/PatternP2PTunnelPart.java");
        String remote = read("src/main/java/cn/ae2bc/logic/RemoteReturnInventory.java");
        String output = read("src/main/java/cn/ae2bc/logic/PatternP2PTunnelOutputLogic.java");

        assertTrue(part.contains("outputLogic.filterReturnAmount(what, amount)"));
        assertTrue(count(remote, "amount = filteredAmount(what, amount);") >= 2,
                "slot-based and storage-based inserts must share the task return filter");
        assertTrue(output.contains("ProductExtractor.extract(ExtractionSource.fromTypeMap(sources),\n"
                + "                output.getReturnInventory()"));
        assertTrue(output.contains("return returnBatch.filter(what, amount, returnMode);"));
    }

    @Test
    void everyUnitReturnPathUsesTheManagerFilter() throws Exception {
        String manager = read("src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java");
        String port = read("src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java");

        assertTrue(manager.contains("ReturnMode.STRICT\n"
                + "                && !declaredOutputs.containsKey(what) ? 0 : amount;"));
        assertTrue(manager.contains("long filtered = filterReturned(what, amount);"));
        assertTrue(manager.contains("insertReturned(AEKey what, long amount, Actionable mode)"));

        String collect = method(port, "private boolean collectDroppedItems", "private long handleCollected");
        assertTrue(collect.contains(".insertReturned(what, amount, mode)"),
                "collect ports must insert through the manager filter");

        String collected = method(port, "private long handleCollected", "private boolean updateRedstone");
        assertTrue(collected.contains("simulateReturned(what, amount)"),
                "break/collect admission simulation must use the manager filter");
        assertTrue(collected.contains("insertReturned(what, amount, Actionable.MODULATE)"),
                "break recovery must use the manager filter");

        assertTrue(port.contains("return isReturnPort() || type == PatternP2PUnitPortType.EXTRACT;"),
                "extraction ports must expose the filtered internal return inventory");
        assertTrue(port.contains("? manager.getLogic().insertReturned(what, amount, mode) : 0;"),
                "return inventory insertion must use the manager filter");
    }

    @Test
    void activeDeclaredOutputsArePersistedWhileReturnPolicyStaysDynamic() throws Exception {
        String output = read("src/main/java/cn/ae2bc/logic/PatternP2PTunnelOutputLogic.java");
        String manager = read("src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java");

        assertTrue(output.contains("returnBatch.begin(pattern.getDefinition(), metadata.declaredOutputs()"));
        assertTrue(output.contains("data.remove(\"ActiveReturnMode\")"));
        assertTrue(output.contains("data.put(DECLARED_OUTPUTS"));
        assertTrue(output.contains("data.putInt(ACTIVE_TASK_COUNT"));
        assertTrue(output.contains("returnBatch.load(loadedPattern"));
        assertTrue(output.contains("data.remove(\"ExpectedPrimaryOutputs\")"));

        assertTrue(manager.contains("declaredOutputs.putAll(metadata.declaredOutputs());"));
        assertTrue(manager.contains("getEffectiveConfiguration().returnMode() == ReturnMode.STRICT"));
        assertTrue(manager.contains("data.remove(\"PatternP2PUnitActiveReturnMode\")"));
        assertTrue(manager.contains("data.put(ACTIVE_OUTPUTS"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, "missing method marker: " + startMarker);
        assertTrue(end > start, "missing method boundary: " + endMarker);
        return source.substring(start, end);
    }

    private static int count(String source, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
