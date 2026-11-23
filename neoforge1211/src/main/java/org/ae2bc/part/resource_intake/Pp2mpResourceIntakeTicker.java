package org.ae2bc.part.resource_intake;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;

public final class Pp2mpResourceIntakeTicker implements IGridTickable {
    private final Pp2mpResourceIntakePart part;

    public Pp2mpResourceIntakeTicker(Pp2mpResourceIntakePart part) {
        this.part = part;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false, 1);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return this.part.getLogic().tick(ticksSinceLastCall)
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }
}
