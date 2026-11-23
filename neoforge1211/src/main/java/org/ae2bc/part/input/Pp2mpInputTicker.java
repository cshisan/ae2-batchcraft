package org.ae2bc.part.input;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;

/**
 * 当端点重新可用时，继续派发已持久化的批次任务。
 */
public final class Pp2mpInputTicker implements IGridTickable {
    private final Pp2mpInputPart part;

    public Pp2mpInputTicker(Pp2mpInputPart part) {
        this.part = part;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        this.part.getLogic().resetTickWake();
        return new TickingRequest(
                TickRates.Interface.getMin(), TickRates.Interface.getMax(), false, TickRates.Interface.getMin());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.part.getLogic().resetTickWake();
        boolean recovered = this.part.getLogic().drainInputRecovery();
        if (!this.part.getData().hasBatchTask() && this.part.getData().getInputRecovery().isEmpty()) {
            return TickRateModulation.SLEEP;
        }
        return (this.part.getLogic().dispatchBatchTask() || recovered)
                ? TickRateModulation.URGENT
                : TickRateModulation.SLOWER;
    }
}
