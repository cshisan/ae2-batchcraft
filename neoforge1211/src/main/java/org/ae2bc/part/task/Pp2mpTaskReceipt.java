package org.ae2bc.part.task;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 未确认回执阻止端点接收下一任务，避免输入端重试时重复交付。
 */
public final class Pp2mpTaskReceipt {
    private static final String PENDING_RECEIPT = "PendingBatchReceipt";
    private @Nullable UUID pending;

    public boolean isPending() {
        return this.pending != null;
    }

    public boolean matches(UUID taskId) {
        return taskId != null && taskId.equals(this.pending);
    }

    public void remember(UUID taskId) {
        if (this.pending != null && !this.pending.equals(taskId)) {
            throw new IllegalStateException("Previous batch receipt is not acknowledged");
        }
        this.pending = taskId;
    }

    public void acknowledge(UUID taskId) {
        if (this.matches(taskId)) {
            this.pending = null;
        }
    }

    public void writeToNBT(CompoundTag data) {
        if (this.pending == null) {
            data.remove(Pp2mpTaskReceipt.PENDING_RECEIPT);
        } else {
            data.putUUID(Pp2mpTaskReceipt.PENDING_RECEIPT, this.pending);
        }
    }

    public void readFromNBT(CompoundTag data) {
        this.pending = data.hasUUID(Pp2mpTaskReceipt.PENDING_RECEIPT)
                ? data.getUUID(Pp2mpTaskReceipt.PENDING_RECEIPT) : null;
    }
}
