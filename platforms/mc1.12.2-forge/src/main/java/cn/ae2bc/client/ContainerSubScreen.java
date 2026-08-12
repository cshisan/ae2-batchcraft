package cn.ae2bc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/** Switches screens without invoking GuiContainer.onGuiClosed on the shared parent container. */
final class ContainerSubScreen {
    private ContainerSubScreen() { }

    static void switchTo(GuiScreen next) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.currentScreen = null;
        minecraft.displayGuiScreen(next);
    }
}
