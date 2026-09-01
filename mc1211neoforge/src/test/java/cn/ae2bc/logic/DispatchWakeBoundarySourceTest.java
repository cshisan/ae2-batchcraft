package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchWakeBoundarySourceTest {
    @Test
    void providerOwnedRemainderRemovesPlanningBackoffButKeepsEventWake() throws Exception {
        String source = source("logic/PatternP2PTunnelInputLogic.java");
        assertTrue(!source.contains("BatchFailureKey"));
        assertTrue(!source.contains("DispatchBackoffPolicy.planningDelay"));
        assertTrue(source.contains("now < batchNextRetryTick"));
        assertTrue(source.contains("DispatchBackoffPolicy.pendingDelay"));
        assertTrue(source.contains("invalidateOutputAvailability()"));
        assertTrue(source.contains("alertBatchRetry();"));
        assertTrue(source.contains("batchContext.roundUnits() == 0 && batchContext.remainingUnits() > 0"));
        assertTrue(source.contains("alertBatchProvider();"));
        assertTrue(source.contains("grid.getTickManager().alertDevice(providerNode)"));
        assertTrue(source.contains("batchContext = null;"));
        assertTrue(source.contains("batchProviderNode = providerNode;"));
    }

    @Test
    void atomicCapacityUsesSharedExponentialProbe() throws Exception {
        String output = source("logic/PatternP2PTunnelOutputLogic.java");
        assertTrue(output.contains("AtomicTaskCapacityProbe.findMaximum(upperBound"));
        assertTrue(output.contains("canAcceptAtomicUnits(pattern, atomicMetadata, atomicInputs, units, sessionId, source)"));

        String manager = source("logic/PatternP2PUnitManagerLogic.java");
        assertTrue(manager.contains("AtomicTaskCapacityProbe.findMaximum(upperBound"));
        assertTrue(manager.contains("canAcceptAtomicUnits(pattern, atomicMetadata, atomicInputs, units, sessionId)"));
    }

    @Test
    void outputAndUnitRemaindersKeepDeadlineRetriesAndEventWake() throws Exception {
        String output = source("logic/PatternP2PTunnelOutputLogic.java");
        assertTrue(output.contains("now < pendingNextRetryTick"));
        assertTrue(output.contains("DispatchBackoffPolicy.pendingDelay"));
        assertTrue(output.contains("public void onTargetChanged()"));
        assertTrue(output.contains("output.notifyInputAvailabilityChanged();"));

        String manager = source("logic/PatternP2PUnitManagerLogic.java");
        assertTrue(manager.contains("now < pendingNextRetryTick"));
        assertTrue(manager.contains("public void alertPendingRetry()"));
        assertTrue(manager.contains("manager.notifyInputAvailabilityChanged();"));

        String port = source("part/PatternP2PUnitPortPart.java");
        assertTrue(port.contains("this::wakeForTargetChange"));
        assertTrue(port.contains("manager.getLogic().alertPendingRetry();"));
    }

    @Test
    void unitPortDispatchFiltersBeforePriorityAndDistinguishesRejectAndPartialInsert() throws Exception {
        String manager = source("logic/PatternP2PUnitManagerLogic.java");
        assertTrue(manager.contains("candidatePorts(\n                    portsFor(boundPorts, pending.form()), pending)"));
        assertTrue(manager.contains("if (inserted <= 0)"));
        assertTrue(manager.contains("continue;\n                }\n                inserted = Math.min(inserted, remaining);"));
        assertTrue(manager.contains("dispatchSlotPorts.putIfAbsent(pending.slot(), port)"));
        assertTrue(manager.contains("remaining <= 0 && (material.slot() < 0 || !assignedSlotPort.containsKey(material.slot()))")
                || manager.contains("material.slot() < 0 || !assignedSlotPort.containsKey(material.slot())"));

        String port = source("part/PatternP2PUnitPortPart.java");
        assertTrue(port.contains("public boolean matchesInput"));
        assertTrue(port.contains("UnitPortType.forOutputFormId(form.getId()) == type"));
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc").resolve(relativePath));
    }
}
