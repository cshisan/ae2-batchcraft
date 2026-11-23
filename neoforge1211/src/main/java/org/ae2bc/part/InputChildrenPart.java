package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.parts.AEBasePart;
import org.ae2bc.core.part.ConfigLocal;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public interface InputChildrenPart {
    short getFrequency();

    /* 查找输入端 */
    default @Nullable Pp2mpInputPart findInput(short fred, IGrid grid) {
        if (grid == null || fred == 0) {
            return null;
        }
        return Pp2mpGridService.get(grid).findInput(fred);
    }

    /* 设置频率 */
    default void setFrequency(
            AEBasePart part, short freq,
            ConfigLocal<InputConfig> configLocal,
            BooleanSupplier taskActive, Runnable setFreq
    ) {
        if (taskActive.getAsBoolean() || this.getFrequency() == freq) {
            return;
        }
        IGrid grid = part.getMainNode().getGrid();

        // 解绑
        Pp2mpInputPart previousInput = this.findInput(this.getFrequency(), grid);
        if (previousInput != null) {
            previousInput.getData().eventSource().unregister(configLocal);
        }

        // 绑定
        Pp2mpInputPart currInput = this.findInput(freq, grid);
        setFreq.run();
        if (currInput != null) {
            currInput.getData().eventSource().register(configLocal);
            // 应用新输入端配置
            configLocal.apply(currInput.getData().getConfig());
        }

        // 通知网络更新
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
    }
}
