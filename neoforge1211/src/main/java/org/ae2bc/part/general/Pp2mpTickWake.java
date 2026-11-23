package org.ae2bc.part.general;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.ITickManager;
import org.jetbrains.annotations.Nullable;

/* 只合并已成功入队、尚未执行的唤醒；节点调度及重新注册时必须清除标记。 */
public final class Pp2mpTickWake {
    private @Nullable IGridNode pendingNode;
    private @Nullable ITickManager pendingManager;

    /* 失败的唤醒不记账，网络或节点身份变化后重新提交。 */
    public void alert(IManagedGridNode mainNode) {
        IGrid grid = mainNode.getGrid();
        IGridNode node = mainNode.getNode();
        if (grid == null || node == null) {
            this.reset();
            return;
        }
        ITickManager manager = grid.getTickManager();
        if (node == this.pendingNode && manager == this.pendingManager) {
            return;
        }
        this.reset();
        if (manager.alertDevice(node)) {
            this.pendingNode = node;
            this.pendingManager = manager;
        }
    }

    /* 调度入口先消费标记，执行后同一 tick 的新工作仍可唤醒。 */
    public void reset() {
        this.pendingNode = null;
        this.pendingManager = null;
    }
}
