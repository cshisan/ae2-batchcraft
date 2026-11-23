package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.unit.UnitPortType;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitExtractPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitCollectPortPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitBreakPortPart;
import org.ae2bc.storage.ProductExtractor;
import org.ae2bc.storage.ReceiveInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ManagerCooldownTest {
    @Test
    void extractionCollectionAndBreakingCanAllProgressEveryTick() {
        Fixture fixture = new Fixture((short) 1);
        Pp2mpUnitCollectPortPart collector = Mockito.mock(Pp2mpUnitCollectPortPart.class);
        Pp2mpUnitBreakPortPart breaker = Mockito.mock(Pp2mpUnitBreakPortPart.class);
        Mockito.when(fixture.service.getPorts((short) 1, (short) 2, UnitPortType.COLLECT))
                .thenReturn(List.of(collector));
        Mockito.when(fixture.service.getPorts((short) 1, (short) 2, UnitPortType.BREAK))
                .thenReturn(List.of(breaker));
        Mockito.when(fixture.port.extractProducts(Mockito.any(ProductExtractionParam.class)))
                .thenReturn(new ProductExtractor.Result(1, 1, false));
        Mockito.when(collector.collectProducts(Mockito.anyInt())).thenReturn(true);
        Mockito.when(breaker.breakAdjacentBlock(Mockito.anyBoolean())).thenReturn(true);
        for (int tick = 0; tick < 20; tick++) {
            Assertions.assertEquals(TickRateModulation.URGENT, fixture.logic.tick(1));
        }
        Mockito.verify(fixture.port, Mockito.times(20)).extractProducts(Mockito.any(ProductExtractionParam.class));
        Mockito.verify(collector, Mockito.times(20)).collectProducts(Mockito.anyInt());
        Mockito.verify(breaker, Mockito.times(20)).breakAdjacentBlock(Mockito.anyBoolean());
    }

    @Test
    void unchangedTopologyPreservesIntervalAndRemovalDropsOldCooldown() {
        Fixture fixture = new Fixture();
        fixture.logic.tick(1);
        fixture.logic.tick(1);
        Mockito.verify(fixture.port).extractProducts(Mockito.any(ProductExtractionParam.class));
        fixture.ports = List.of();
        fixture.logic.tick(1);
        fixture.ports = List.of(fixture.port);
        fixture.logic.tick(1);
        Mockito.verify(fixture.port, Mockito.times(2)).extractProducts(Mockito.any(ProductExtractionParam.class));
    }

    @Test
    void replacingAnotherPortPreservesExistingPortsRemainingCooldown() {
        Fixture fixture = new Fixture();
        Pp2mpUnitExtractPortPart second = Mockito.mock(Pp2mpUnitExtractPortPart.class);
        Mockito.when(second.extractProducts(Mockito.any(ProductExtractionParam.class)))
                .thenReturn(new ProductExtractor.Result(0, 0, false));
        fixture.logic.tick(1);
        fixture.ports = List.of(fixture.port, second);
        fixture.logic.tick(1);
        Mockito.verify(fixture.port).extractProducts(Mockito.any(ProductExtractionParam.class));
        Mockito.verify(second).extractProducts(Mockito.any(ProductExtractionParam.class));
        fixture.logic.tick(4);
        Mockito.verify(fixture.port, Mockito.times(2)).extractProducts(Mockito.any(ProductExtractionParam.class));
        Mockito.verify(second).extractProducts(Mockito.any(ProductExtractionParam.class));
    }

    @Test
    void rebindAndPowerRecoveryDoNotReuseDetachedCooldowns() {
        Fixture fixture = new Fixture();
        fixture.logic.tick(1);
        Mockito.when(fixture.part.getUnitId()).thenReturn((short) 3);
        fixture.logic.tick(1);
        Mockito.when(fixture.part.getUnitId()).thenReturn((short) 2);
        fixture.logic.tick(1);
        Mockito.verify(fixture.port, Mockito.times(2)).extractProducts(Mockito.any(ProductExtractionParam.class));
        Mockito.when(fixture.node.isActive()).thenReturn(false);
        fixture.logic.tick(1);
        Mockito.when(fixture.node.isActive()).thenReturn(true);
        fixture.logic.tick(1);
        Mockito.verify(fixture.port, Mockito.times(3)).extractProducts(Mockito.any(ProductExtractionParam.class));
    }

    private static final class Fixture {
        private final IGrid grid = Mockito.mock(IGrid.class);
        private final IManagedGridNode node = Mockito.mock(IManagedGridNode.class);
        private final Pp2mpGridService service = Mockito.mock(Pp2mpGridService.class);
        private final Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
        private final Pp2mpUnitExtractPortPart port = Mockito.mock(Pp2mpUnitExtractPortPart.class);
        private List<Pp2mpUnitPortPart> ports = List.of(this.port);
        private final Pp2mpUnitManagerLogic logic;

        private Fixture() {
            this((short) 5);
        }

        private Fixture(short interval) {
            Pp2mpUnitManagerData data = new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { });
            data.apply(InputConfig.builder(InputConfig.DEFAULT).productExtractionInterval(interval).build());
            data.begin(Map.of(AEItemKey.of(Items.DIAMOND), 100L),
                    null, UUID.randomUUID(), null);
            Mockito.when(this.part.getData()).thenReturn(data);
            Mockito.when(this.part.getFrequency()).thenReturn((short) 1);
            Mockito.when(this.part.getUnitId()).thenReturn((short) 2);
            Mockito.when(this.part.validFrequency()).thenReturn(true);
            ReceiveInventory receiver = new ReceiveInventory(() -> null, Map::of, key -> true,
                    (key, amount) -> { }, IActionSource.empty(), () -> { });
            Mockito.when(this.part.getReceiveInventory()).thenReturn(receiver);
            IPartHost host = Mockito.mock(IPartHost.class);
            Mockito.when(this.part.getHost()).thenReturn(host);
            Mockito.when(this.node.isActive()).thenReturn(true);
            Mockito.when(this.node.getGrid()).thenReturn(this.grid);
            Mockito.when(this.grid.getService(Pp2mpGridService.class)).thenReturn(this.service);
            Mockito.when(this.service.getPorts((short) 1, (short) 2, UnitPortType.EXTRACT))
                    .thenAnswer(call -> this.ports);
            Mockito.when(this.port.extractProducts(Mockito.any(ProductExtractionParam.class)))
                    .thenReturn(new ProductExtractor.Result(0, 0, false));
            this.logic = new Pp2mpUnitManagerLogic(this.node, this.part);
        }
    }
}
