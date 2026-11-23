package org.ae2bc.part;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.part.output.Pp2mpOutputData;
import org.ae2bc.part.output.Pp2mpOutputLogic;
import org.ae2bc.part.output.Pp2mpOutputPart;
import org.ae2bc.part.output.Pp2mpOutputTicker;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerTicker;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.storage.ProductExtractor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

class ExtractionTickerTest {
    @Test
    void outputSupportsContinuousOneTickExtractionAndBacksOffWhenBlocked() {
        Fixture fixture = new Fixture((short) 1);
        TickingRequest request = fixture.ticker.getTickingRequest(fixture.node);
        Assertions.assertEquals(1, request.minTickRate());
        Assertions.assertEquals(1, request.initialTickRate());
        for (int tick = 0; tick < 20; tick++) {
            Assertions.assertEquals(TickRateModulation.URGENT, fixture.ticker.tickingRequest(fixture.node, 1));
        }
        Mockito.verify(fixture.logic, Mockito.times(20)).extractProducts(4096);
        Mockito.when(fixture.logic.extractProducts(4096)).thenReturn(new ProductExtractor.Result(0, 0, true));
        Assertions.assertEquals(TickRateModulation.SLOWER, fixture.ticker.tickingRequest(fixture.node, 1));
    }

    @Test
    void outputStillHonorsLongerIntervalsAndDoesNotCatchUpWithUnboundedBursts() {
        Fixture fixture = new Fixture((short) 5);
        for (int tick = 0; tick < 10; tick++) {
            fixture.ticker.tickingRequest(fixture.node, 1);
        }
        Mockito.verify(fixture.logic, Mockito.times(2)).extractProducts(4096);
        fixture.ticker.tickingRequest(fixture.node, 100);
        Mockito.verify(fixture.logic, Mockito.times(3)).extractProducts(4096);
        Mockito.when(fixture.logic.isOperational()).thenReturn(false);
        Assertions.assertEquals(TickRateModulation.SLEEP, fixture.ticker.tickingRequest(fixture.node, 1));
    }

    @Test
    void unitManagerStartsAndRunsAtOneTick() {
        Pp2mpUnitManagerPart part = Mockito.mock(Pp2mpUnitManagerPart.class);
        Mockito.when(part.getData()).thenReturn(new Pp2mpUnitManagerData(Items.STONE, () -> { }, () -> { }));
        Pp2mpUnitManagerLogic logic = Mockito.spy(
                new Pp2mpUnitManagerLogic(Mockito.mock(IManagedGridNode.class), part));
        Pp2mpUnitManagerTicker ticker = new Pp2mpUnitManagerTicker(logic);
        IGridNode node = Mockito.mock(IGridNode.class);
        Assertions.assertEquals(1, ticker.getTickingRequest(node).minTickRate());
        Assertions.assertEquals(1, ticker.getTickingRequest(node).initialTickRate());
        Mockito.doReturn(TickRateModulation.URGENT).when(logic).tick(1);
        Assertions.assertEquals(TickRateModulation.URGENT, ticker.tickingRequest(node, 1));
    }

    private static final class Fixture {
        private final IGridNode node = Mockito.mock(IGridNode.class);
        private final Pp2mpOutputLogic logic;
        private final Pp2mpOutputTicker ticker;

        private Fixture(short interval) {
            Pp2mpOutputPart part = Mockito.spy(new Pp2mpOutputPart(ItemRegistry.PP2MP_OUTPUT.get()));
            Pp2mpOutputData data = new Pp2mpOutputData(() -> { }, () -> { }, () -> { });
            data.apply(InputConfig.builder(InputConfig.DEFAULT).productExtraction(true)
                    .productExtractionInterval(interval).productExtractionQuantity(4096).build());
            data.begin(Map.of(AEItemKey.of(Items.GRAVEL), 100000L), null, UUID.randomUUID(), null);
            Mockito.when(part.getData()).thenReturn(data);
            this.logic = Mockito.spy(new Pp2mpOutputLogic(part, data));
            Mockito.doReturn(this.logic).when(part).getLogic();
            Mockito.doReturn(true).when(this.logic).isOperational();
            Mockito.doReturn(new ProductExtractor.Result(4096, 4096, false)).when(this.logic).extractProducts(4096);
            this.ticker = new Pp2mpOutputTicker(part);
        }
    }
}
