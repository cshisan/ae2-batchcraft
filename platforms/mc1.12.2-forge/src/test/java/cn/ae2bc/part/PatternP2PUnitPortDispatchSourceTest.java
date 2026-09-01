package cn.ae2bc.part;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** Regression guards for the 1.12.2 unit-port admission and dispatch contract. */
public final class PatternP2PUnitPortDispatchSourceTest {
    @Test
    public void portMatchingIncludesManagerBindingAndFrequency() throws Exception {
        String source = read("PatternP2PUnitPortPart.java");
        assertTrue(source.contains("public boolean isBoundTo(PatternP2PUnitManagerPart manager)"));
        assertTrue(source.contains("boundFrequency == 0 || boundFrequency == manager.getFrequency()"));
        assertTrue(source.contains("return isBoundTo(manager) && stack != null && !stack.isEmpty()"));
    }

    @Test
    public void transferAdmissionUsesAggregateHandlerCapacity() throws Exception {
        String port = read("PatternP2PUnitPortPart.java");
        String manager = read("PatternP2PUnitManagerPart.java");
        assertTrue(port.contains("public int estimateTransferCapacity(ItemStack what, int fallback)"));
        assertTrue(manager.contains("port.estimateTransferCapacity(stack, simulated)"));
        assertTrue(manager.contains("type == cn.ae2bc.core.unit.UnitPortType.TRANSFER"));
    }

    @Test
    public void dispatchRetainsSameTypeAndTriesEveryCandidate() throws Exception {
        String source = read("PatternP2PUnitManagerPart.java");
        assertTrue(source.contains("dispatchPortTypes"));
        assertTrue(source.contains("dispatchPortTypes.containsKey(port)"));
        assertTrue(source.contains("for (PatternP2PUnitPortPart port : ports)"));
        assertTrue(source.contains("if (moved <= 0) continue;"));
    }

    @Test
    public void singleSlotDispatchUsesStablePatternSlotIdentity() throws Exception {
        String source = read("PatternP2PUnitManagerPart.java");
        String tunnel = read("PatternP2PTunnelPart.java");
        assertTrue(source.contains("pendingInputSlots"));
        assertTrue(source.contains("List<Integer> patternSlots"));
        assertTrue(source.contains("pendingInputSlots.add(patternSlots.get(i))"));
        assertTrue(tunnel.contains("dispatch.patternSlots"));
        assertTrue(source.contains("int slot = pendingInputSlots.get(index);"));
        assertTrue(source.contains("canUsePortForSlot(slot, port"));
        assertTrue(source.contains("pendingInputSlots.remove(index);"));
        assertTrue(source.contains("entry.setInteger(\"PatternSlot\", pendingInputSlots.get(i))"));
        assertTrue(source.contains("entry.hasKey(\"PatternSlot\")"));
        assertTrue(source.contains("assignedSlotPorts.putIfAbsent(slot, port)"));
        assertTrue(source.contains("dispatchSlotPorts.putIfAbsent(slot, port)"));
    }

    @Test
    public void singleItemModeOnlyLimitsTransferPortsAndPlacementConsumesOne() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");
        assertTrue(manager.contains("type == cn.ae2bc.core.unit.UnitPortType.TRANSFER && outputMode"));
        assertTrue(manager.contains("type == cn.ae2bc.core.unit.UnitPortType.TRANSFER\n                            && outputMode"));
        assertTrue(port.contains("type == UnitPortType.PLACE && stack.getCount() > 0"));
        assertTrue(port.contains("placing.setCount(1)"));
    }

    private static String read(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/part", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
