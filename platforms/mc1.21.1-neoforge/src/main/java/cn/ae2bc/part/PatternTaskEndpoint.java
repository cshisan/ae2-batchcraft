package cn.ae2bc.part;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import cn.ae2bc.logic.PatternDispatchMetadata;

import java.util.UUID;

/** A frequency-bound destination that can accept one complete crafting task. */
public interface PatternTaskEndpoint {
    String getDispatchId();

    boolean isOperationalTaskEndpoint();

    boolean canAcceptTask();

    boolean isTaskActive();

    boolean hasActiveBatchSession(UUID sessionId);

    boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                             KeyCounter[] inputs, IActionSource source);

    long getMaximumAcceptedAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                       KeyCounter[] atomicInputs, long upperBound, UUID sessionId,
                                       IActionSource source);

    boolean tryAcceptPatternAtomicUnits(IPatternDetails pattern, PatternDispatchMetadata atomicMetadata,
                                        KeyCounter[] atomicInputs, long units, UUID sessionId,
                                        IActionSource source);

    /** Clears the active task and permanently discards ingredients that have not been dispatched yet. */
    void resetTaskState();
}
