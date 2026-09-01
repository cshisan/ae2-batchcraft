package cn.ae2bc.part;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import cn.ae2bc.logic.PatternDispatchMetadata;

/** A frequency-bound destination that can accept one complete crafting task. */
public interface PatternTaskEndpoint {
    boolean isOperationalTaskEndpoint();

    boolean canAcceptTask();

    boolean isTaskActive();

    boolean tryAcceptPattern(IPatternDetails pattern, PatternDispatchMetadata metadata,
                             KeyCounter[] inputs, IActionSource source);

    /** Clears the active task and permanently discards ingredients that have not been dispatched yet. */
    void resetTaskState();
}
