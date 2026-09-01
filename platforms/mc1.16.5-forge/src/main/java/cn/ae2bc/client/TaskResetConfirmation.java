package cn.ae2bc.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Shared task-reset confirmation dialog for legacy screens. */
final class TaskResetConfirmation {
    private TaskResetConfirmation() {
    }

    static void open(Screen parent, ITextComponent warning, Runnable resetAction) {
        Minecraft minecraft = Minecraft.getInstance();
        BooleanConsumer callback = confirmed -> {
            minecraft.setScreen(parent);
            if (confirmed) {
                resetAction.run();
            }
        };
        minecraft.setScreen(new ConfirmScreen(callback,
                new TranslationTextComponent("gui.ae2_batchcraft.reset_task.confirm.title"), warning));
    }
}
