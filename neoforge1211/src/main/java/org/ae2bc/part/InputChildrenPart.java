package org.ae2bc.part;

import appeng.api.networking.IGrid;
import appeng.parts.AEBasePart;
import org.ae2bc.part.general.Pp2mpGridService;
import org.ae2bc.part.input.Pp2mpInputPart;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public interface InputChildrenPart {
    short getFrequency();

    /* 查找输入端 */
    default @Nullable Pp2mpInputPart findInput(short fred, @Nullable IGrid grid) {
        if (grid == null || fred == 0) {
            return null;
        }
        return Pp2mpGridService.get(grid).findInput(fred);
    }

    /* 设置频率 */
    default void setFrequency(
            AEBasePart part, short freq,
            BooleanSupplier taskActive, Runnable setFreq, Runnable refreshConfig
    ) {
        if (taskActive.getAsBoolean() || this.getFrequency() == freq) {
            return;
        }
        IGrid grid = part.getMainNode().getGrid();

        setFreq.run();
        if (grid != null) {
            Pp2mpGridService.get(grid).topologyChanged();
        }
        refreshConfig.run();
    }
}
