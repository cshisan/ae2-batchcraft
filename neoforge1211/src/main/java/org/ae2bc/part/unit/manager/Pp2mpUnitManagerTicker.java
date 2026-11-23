package org.ae2bc.part.unit.manager;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

public class Pp2mpUnitManagerTicker implements IGridTickable {
    private final Pp2mpUnitManagerLogic logic;

    public Pp2mpUnitManagerTicker(Pp2mpUnitManagerLogic logic) {
        this.logic = logic;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        this.logic.resetTickWake();
        return new TickingRequest(1, 20, false, 1);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return this.logic.tick(ticksSinceLastCall);
    }
}
