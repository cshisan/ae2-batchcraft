package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;

/** Switches screens without invoking ContainerScreen.removed on the shared parent menu. */
final class ContainerSubScreen {
    private ContainerSubScreen() { }

    static void switchTo(Screen next) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.screen = null;
        minecraft.setScreen(next);
    }
}
