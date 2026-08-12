package cn.ae2bc.client;

import appeng.client.gui.me.items.PatternTermScreen;
import cn.ae2bc.Ae2bcMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Claims the material-output shortcut before inventory sorting mods consume middle-click. */
@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, value = Dist.CLIENT)
public final class PatternEncodingTermScreenEvents {
    private PatternEncodingTermScreenEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void mouseClicked(GuiScreenEvent.MouseClickedEvent.Pre event) {
        if (!(event.getGui() instanceof PatternTermScreen)) {
            return;
        }
        PatternTermScreen screen = (PatternTermScreen) event.getGui();
        if (PatternEncodingTermScreenSupport.handleMouseClicked(
                screen, screen.getSlotUnderMouse(), event.getButton())) {
            event.setCanceled(true);
        }
    }
}
