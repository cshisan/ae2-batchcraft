package org.ae2bc.part.task;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface Pp2mpTaskEndpoint {
    @Nullable String getEndpointId();

    boolean isOperational();

    boolean canAcceptTask();

    boolean isTaskActive();

    /* 重试已接收的任务只确认回执，不能重新交付材料或创建转换器票据。 */
    boolean hasAcceptedTask(UUID taskId);

    default void holdBatchReceipt(UUID taskId) {
    }

    default void acknowledgeBatch(UUID taskId) {
    }

    /* 样板和材料表示本次完整交付，端点直接按样板数量记录产物，不再拆批或缩放。 */
    boolean tryAcceptTask(
            IPatternDetails pattern, KeyCounter[] inputs,
            IActionSource source, UUID taskId
    );

    void resetTaskState();
}
