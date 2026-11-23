package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.me.service.TickManagerService;
import org.ae2bc.part.general.Pp2mpTickWake;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class TickWakeTest {
    @Test
    void coalescesOnlyUntilExecutionAndAllowsNewWorkAfterSleepOrBackoff() {
        for (TickRateModulation result : List.of(TickRateModulation.SLEEP, TickRateModulation.SLOWER)) {
            Fixture fixture = new Fixture();
            fixture.ticker.result = result;
            for (int index = 0; index < 100; index++) {
                fixture.wake.alert(fixture.main);
            }
            Mockito.verify(fixture.manager).alertDevice(fixture.node);
            fixture.tick();
            Assertions.assertEquals(1, fixture.ticker.executions);
            // 不推进服务器 tick，执行后的新工作仍必须重新加急。
            fixture.wake.alert(fixture.main);
            fixture.wake.alert(fixture.main);
            Mockito.verify(fixture.manager, Mockito.times(2)).alertDevice(fixture.node);
            fixture.tick();
            Assertions.assertEquals(2, fixture.ticker.executions);
        }
    }

    @Test
    void failedAlertDuringExecutionDoesNotSuppressLaterWake() {
        Fixture fixture = new Fixture();
        fixture.ticker.alertInside = true;
        fixture.wake.alert(fixture.main);
        fixture.tick();
        Mockito.verify(fixture.manager, Mockito.times(2)).alertDevice(fixture.node);
        fixture.wake.alert(fixture.main);
        Mockito.verify(fixture.manager, Mockito.times(3)).alertDevice(fixture.node);
        fixture.tick();
        Assertions.assertEquals(2, fixture.ticker.executions);
    }

    @Test
    void reRegistrationOfSameNodeClearsPendingAlert() {
        Fixture fixture = new Fixture();
        fixture.wake.alert(fixture.main);
        fixture.manager.removeNode(fixture.node);
        fixture.manager.addNode(fixture.node, null);
        fixture.wake.alert(fixture.main);
        Mockito.verify(fixture.manager, Mockito.times(2)).alertDevice(fixture.node);
        fixture.tick();
        Assertions.assertEquals(1, fixture.ticker.executions);
    }

    @Test
    void managerNodeAndDisconnectChangesDoNotReuseOldPendingState() {
        Fixture fixture = new Fixture();
        fixture.wake.alert(fixture.main);
        ITickManager replacement = Mockito.mock(ITickManager.class);
        Mockito.when(replacement.alertDevice(Mockito.any(IGridNode.class))).thenReturn(true);
        Mockito.when(fixture.grid.getTickManager()).thenReturn(replacement);
        fixture.wake.alert(fixture.main);
        Mockito.verify(replacement).alertDevice(fixture.node);
        IGridNode newNode = Mockito.mock(IGridNode.class);
        Mockito.when(fixture.main.getNode()).thenReturn(newNode);
        fixture.wake.alert(fixture.main);
        Mockito.verify(replacement).alertDevice(newNode);
        Mockito.when(fixture.main.getGrid()).thenReturn(null);
        fixture.wake.alert(fixture.main);
        Mockito.when(fixture.main.getGrid()).thenReturn(fixture.grid);
        fixture.wake.alert(fixture.main);
        Mockito.verify(replacement, Mockito.times(2)).alertDevice(newNode);
    }

    @Test
    void exceptionDoesNotLeaveSuccessfulWakeMarker() {
        Fixture fixture = new Fixture();
        ITickManager manager = Mockito.mock(ITickManager.class);
        Mockito.when(fixture.grid.getTickManager()).thenReturn(manager);
        Mockito.when(manager.alertDevice(fixture.node)).thenThrow(new IllegalStateException("test")).thenReturn(true);
        Assertions.assertThrows(IllegalStateException.class, () -> fixture.wake.alert(fixture.main));
        fixture.wake.alert(fixture.main);
        fixture.wake.alert(fixture.main);
        Mockito.verify(manager, Mockito.times(2)).alertDevice(fixture.node);
    }

    private static final class Fixture {
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final IGridNode node = Mockito.mock(IGridNode.class);
        private final IManagedGridNode main = Mockito.mock(IManagedGridNode.class);
        private final TickManagerService manager = Mockito.spy(new TickManagerService());
        private final Pp2mpTickWake wake = new Pp2mpTickWake();
        private final Ticker ticker = new Ticker(this.wake, this.main);

        private Fixture() {
            Mockito.when(this.grid.getTickManager()).thenReturn(this.manager);
            Mockito.when(this.main.getGrid()).thenReturn(this.grid);
            Mockito.when(this.main.getNode()).thenReturn(this.node);
            Mockito.when(this.node.getService(IGridTickable.class)).thenReturn(this.ticker);
            this.manager.addNode(this.node, null);
        }

        private void tick() {
            this.manager.onServerStartTick();
            this.manager.onServerEndTick();
        }
    }

    private static final class Ticker implements IGridTickable {
        private final Pp2mpTickWake wake;
        private final IManagedGridNode main;
        private TickRateModulation result = TickRateModulation.SLEEP;
        private boolean alertInside;
        private int executions;

        private Ticker(Pp2mpTickWake wake, IManagedGridNode main) {
            this.wake = wake;
            this.main = main;
        }

        @Override
        public TickingRequest getTickingRequest(@NonNull IGridNode node) {
            this.wake.reset();
            return new TickingRequest(1, 20, true, 5);
        }

        @Override
        public TickRateModulation tickingRequest(@NonNull IGridNode node, int ticksSinceLastCall) {
            this.wake.reset();
            this.executions++;
            if (this.alertInside) {
                this.wake.alert(this.main);
            }
            return this.result;
        }
    }
}
