package org.ae2bc.part.output;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.storage.ProductExtractor;

/* 统一调度输出端材料余量发送、取消任务回退和产物提取。 */
public final class Pp2mpOutputTicker implements IGridTickable {
    private final Pp2mpOutputPart part;
    private int cooldown;

    public Pp2mpOutputTicker(Pp2mpOutputPart part) {
        this.part = part;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        this.part.resetTickWake();
        return new TickingRequest(
                1, Math.max(1, TickRates.Interface.getMax()), false, 1
        );
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.part.resetTickWake();
        Pp2mpOutputData data = this.part.getData();
        Pp2mpOutputLogic logic = this.part.getLogic();
        logic.retryTaskCompletion();
        if (!logic.isOperational()) {
            this.cooldown = 0;
            return TickRateModulation.SLEEP;
        }

        boolean sent = logic.drainPendingInputs();
        boolean recovered = logic.drainTaskRecovery();
        boolean pending = !data.getPendingInputs().isEmpty() || data.hasRecovery()
                || (data.isTaskActive() && (data.isCancelRequested()
                || !data.getCompletions().isEmpty()));
        if (!data.isTaskActive() || data.isCancelRequested() || !data.getConfig().output().productExtraction()) {
            this.cooldown = 0;
            return pending ? (sent || recovered ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                    : TickRateModulation.SLEEP;
        }

        this.cooldown -= ticksSinceLastCall;
        if (this.cooldown > 0) {
            return sent || recovered ? TickRateModulation.URGENT : TickRateModulation.SAME;
        }

        ProductExtractor.Result result;
        if (!data.hasRecovery()) {
            ProductExtractionParam param = data.getConfig().general().extractionParam();
            result = logic.extractProducts(param.getQuantity());
        } else {
            result = new ProductExtractor.Result(0, 0, true);
        }

        this.cooldown = Math.max(1, data.getConfig().general().extractionParam().getInterval());
        return sent || recovered || result.movedOperations() > 0
                ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }
}
