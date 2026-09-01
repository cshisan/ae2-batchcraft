package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;

/** 1.12.2 equivalent of the modern ConfirmScreen-based reset flow. */
final class TaskResetConfirmation {
    private TaskResetConfirmation() {
    }

    static void open(GuiScreen parent, String warningKey, Runnable resetAction) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.displayGuiScreen(new GuiYesNo((confirmed, ignored) -> {
            minecraft.displayGuiScreen(parent);
            if (confirmed) {
                resetAction.run();
            }
        }, I18n.format("gui.ae2_batchcraft.reset_task.confirm.title"),
                I18n.format(warningKey), 0));
    }
}
