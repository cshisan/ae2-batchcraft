package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitProviderPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitExtractPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitTransferPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortData;
import org.ae2bc.part.unit.port.base.Pp2mpUnitOutputTypePortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.List;

class GridInvalidationTest {
    @Test
    void orderedPortsInvalidateOnPriorityChangesWithoutRescanningTheGrid() {
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpUnitTransferPortPart first = Mockito.mock(Pp2mpUnitTransferPortPart.class);
        Pp2mpUnitTransferPortPart second = Mockito.mock(Pp2mpUnitTransferPortPart.class);
        IGridNode firstNode = Mockito.mock(IGridNode.class);
        IGridNode secondNode = Mockito.mock(IGridNode.class);
        Mockito.when(firstNode.getOwner()).thenReturn(first);
        Mockito.when(secondNode.getOwner()).thenReturn(second);
        Mockito.when(grid.getMachineClasses()).thenReturn(List.of(Pp2mpUnitTransferPortPart.class));
        Mockito.when(grid.getMachineNodes(Pp2mpUnitTransferPortPart.class)).thenReturn(List.of(firstNode, secondNode));
        for (Pp2mpUnitTransferPortPart port : List.of(first, second)) {
            Mockito.when(port.getFrequency()).thenReturn((short) 1);
            Mockito.when(port.getUnitId()).thenReturn((short) 2);
            Mockito.when(port.getType()).thenReturn(UnitPortType.TRANSFER);
            Mockito.when(port.getData()).thenReturn(new Pp2mpUnitOutputTypePortData(() -> { }, () -> { }));
        }
        Pp2mpGridService service = new Pp2mpGridService(grid);
        List<Pp2mpUnitOutputTypePortPart> original = service.getOutputPorts((short) 1, (short) 2, UnitPortType.TRANSFER);
        Assertions.assertSame(original, service.getOutputPorts((short) 1, (short) 2, UnitPortType.TRANSFER));
        Mockito.clearInvocations(grid);
        second.getData().setPriority(10);
        service.portOrderChanged();
        Assertions.assertEquals(List.of(second, first), service.getOutputPorts((short) 1, (short) 2, UnitPortType.TRANSFER));
        Assertions.assertEquals(List.of(first, second), original);
        Mockito.verifyNoInteractions(grid);
        Mockito.when(grid.getMachineNodes(Pp2mpUnitTransferPortPart.class)).thenReturn(List.of(firstNode));
        service.topologyChanged();
        Assertions.assertEquals(List.of(first), service.getOutputPorts((short) 1, (short) 2, UnitPortType.TRANSFER));
    }

    @Test
    void portSnapshotsAreStableReadOnlyAndReplacedAfterRebinding() {
        IGrid grid = Mockito.mock(IGrid.class);
        IGridNode node = Mockito.mock(IGridNode.class);
        Pp2mpUnitExtractPortPart port = Mockito.mock(Pp2mpUnitExtractPortPart.class);
        Mockito.when(grid.getMachineClasses()).thenReturn(List.of(Pp2mpUnitExtractPortPart.class));
        Mockito.when(grid.getMachineNodes(Pp2mpUnitExtractPortPart.class)).thenReturn(List.of(node));
        Mockito.when(node.getOwner()).thenReturn(port);
        Mockito.when(port.getFrequency()).thenReturn((short) 1);
        Mockito.when(port.getUnitId()).thenReturn((short) 2);
        Mockito.when(port.getType()).thenReturn(UnitPortType.EXTRACT);
        Pp2mpGridService service = new Pp2mpGridService(grid);
        List<Pp2mpUnitPortPart> first = service.getPorts((short) 1, (short) 2, UnitPortType.EXTRACT);
        Assertions.assertEquals(List.of(port), first);
        Assertions.assertSame(first, service.getPorts((short) 1, (short) 2, UnitPortType.EXTRACT));
        Assertions.assertThrows(UnsupportedOperationException.class, first::clear);
        Mockito.when(port.getUnitId()).thenReturn((short) 3);
        service.topologyChanged();
        Assertions.assertTrue(service.getPorts((short) 1, (short) 2, UnitPortType.EXTRACT).isEmpty());
        List<Pp2mpUnitPortPart> second = service.getPorts((short) 1, (short) 3, UnitPortType.EXTRACT);
        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(List.of(port), second);
        Mockito.when(grid.getMachineNodes(Pp2mpUnitExtractPortPart.class)).thenReturn(List.of());
        service.topologyChanged();
        Assertions.assertTrue(service.getPorts((short) 1, (short) 3, UnitPortType.EXTRACT).isEmpty());
        Assertions.assertEquals(List.of(port), first);
    }

    @Test
    void returnInventoryNotificationsWakeEndpointsWithoutDiscardingCaches() {
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
        Pp2mpUnitManagerPart manager = Mockito.mock(Pp2mpUnitManagerPart.class);
        Pp2mpUnitManagerLogic logic = Mockito.mock(Pp2mpUnitManagerLogic.class);
        Mockito.when(output.getFrequency()).thenReturn((short) 1);
        Mockito.when(output.getLogic()).thenReturn(Mockito.mock(Pp2mpOutputLogic.class));
        Mockito.when(manager.getFrequency()).thenReturn((short) 1);
        Mockito.when(manager.getLogic()).thenReturn(logic);
        Mockito.when(grid.getMachines(Pp2mpOutputPart.class)).thenReturn(Set.of(output));
        Mockito.when(grid.getMachines(Pp2mpUnitManagerPart.class)).thenReturn(Set.of(manager));
        Pp2mpGridService service = new Pp2mpGridService(grid);
        service.wakeReturnTargets((short) 1);
        Mockito.verify(output).wakeProductExtractor();
        Mockito.verify(logic).alertPendingRetry();
        Mockito.verify(output, Mockito.never()).clearReceiveTargetCache();
        Mockito.verify(manager, Mockito.never()).clearReceiveStorageCache();
    }

    @Test
    void managerChildPortsConsumeTheSharedVisitBudgetEvenWhenUnavailable() {
        IGrid grid = Mockito.mock(IGrid.class);
        IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
        Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        Mockito.when(part.getData()).thenReturn(data);
        Mockito.when(part.getFrequency()).thenReturn((short) 1);
        Mockito.when(part.getUnitId()).thenReturn((short) 2);
        Mockito.when(part.validFrequency()).thenReturn(true);
        Mockito.when(part.getLevel()).thenReturn(Mockito.mock(ServerLevel.class));
        Mockito.when(node.isActive()).thenReturn(true);
        Mockito.when(node.getGrid()).thenReturn(grid);
        Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(service);
        Pp2mpUnitProviderPortPart unavailablePort =
                new Pp2mpUnitProviderPortPart(ItemRegistry.PP2MP_UNIT_PORT_PROVIDER.get());
        Mockito.when(service.getPorts((short) 1, (short) 2, UnitPortType.PROVIDER))
                .thenReturn(Collections.nCopies(300, unavailablePort));
        Pp2mpUnitManagerLogic logic = new Pp2mpUnitManagerLogic(node, part);
        WorkBudget budget = new WorkBudget(10);
        Assertions.assertEquals(0, logic.planProvider(AEItemKey.of(Items.STONE), 100, budget, true).amount());
        Assertions.assertEquals(0, budget.remaining());
    }

    @Test
    void configChangesRefreshRulesWithoutRebuildingAndTopologyRefreshesBoth() {
        IGrid grid = Mockito.mock(IGrid.class);
        Pp2mpOutputPart output = Mockito.mock(Pp2mpOutputPart.class);
        Mockito.when(output.getFrequency()).thenReturn((short) 1);
        Mockito.when(output.getLogic()).thenReturn(Mockito.mock(Pp2mpOutputLogic.class));
        Mockito.when(grid.getMachines(Pp2mpOutputPart.class)).thenReturn(Set.of(output));
        Pp2mpGridService service = new Pp2mpGridService(grid);
        service.onServerEndTick();
        Mockito.clearInvocations(grid, output);

        service.resourceIntakeConfigChanged();
        service.onServerEndTick();
        service.onServerEndTick();
        Mockito.verifyNoInteractions(grid);
        Mockito.verify(output).refreshProviderFilter();

        service.topologyChanged();
        service.onServerEndTick();
        Mockito.verify(grid).getMachines(Pp2mpInputPart.class);
        Mockito.verify(output, Mockito.times(2)).refreshProviderFilter();
        Mockito.clearInvocations(grid);
        service.getOutputs((short) 1);
        service.getManagers((short) 1);
        Mockito.verifyNoInteractions(grid);
    }

    @Test
    void managerCompletionWakesDispatcherWithoutInvalidatingTopology() {
        IGrid grid = Mockito.mock(IGrid.class);
        IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
        Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
        Mockito.when(part.getData()).thenReturn(data);
        Mockito.when(part.getHost()).thenReturn(Mockito.mock(IPartHost.class));
        Mockito.when(part.getFrequency()).thenReturn((short) 1);
        Mockito.when(node.getGrid()).thenReturn(grid);
        Mockito.when(grid.getService(Pp2mpGridService.class)).thenReturn(service);
        Pp2mpUnitManagerLogic logic = new Pp2mpUnitManagerLogic(node, part);
        UUID taskId = UUID.randomUUID();
        data.begin(Map.of(), null, taskId, null);

        logic.tick(1);
        Assertions.assertFalse(data.isTaskActive());
        Assertions.assertEquals(taskId, data.getLastTaskId());
        Mockito.verify(service).wakeBatchInput((short) 1);
        Mockito.verify(service, Mockito.never()).topologyChanged();
        Mockito.verify(service, Mockito.never()).resourceIntakeConfigChanged();
    }
}
