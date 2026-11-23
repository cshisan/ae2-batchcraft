package org.ae2bc.client.screen.component;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.menu.AEBaseMenu;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.ScreenHeader;

public final class TaskResetConfirmation<T extends AEBaseMenu> extends AESubScreen<T, AEBaseScreen<T>> {
    private TaskResetConfirmation(AEBaseScreen<T> parent, Component warning, Runnable resetAction) {
        super(parent, "/screens/ae2_batchcraft/task_reset_confirmation.json");
        ScreenHeader.addTo(this.widgets);
        this.setTextContent("warning", warning);
        this.widgets.addButton("confirm", CommonComponents.GUI_YES, () -> {
            this.returnToParent();
            resetAction.run();
        });
        this.widgets.addButton("cancel", CommonComponents.GUI_NO, this::returnToParent);
    }

    /* 复用 AE2 子界面切换，取消或关闭时恢复父界面及槽位。 */
    public static <T extends AEBaseMenu> void open(AEBaseScreen<T> parent, Component warning, Runnable resetAction) {
        parent.switchToScreen(new TaskResetConfirmation<>(parent, warning, resetAction));
    }

    @Override
    public void onClose() {
        this.returnToParent();
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }
}
