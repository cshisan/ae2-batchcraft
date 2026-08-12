package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

final class TaskResetConfirmation {
    private TaskResetConfirmation() {
    }

    static void open(Screen parent, Component warning, Runnable resetAction) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(parent);
            if (confirmed) {
                resetAction.run();
            }
        }, Component.translatable("gui.ae2_batchcraft.reset_task.confirm.title"), warning));
    }
}
