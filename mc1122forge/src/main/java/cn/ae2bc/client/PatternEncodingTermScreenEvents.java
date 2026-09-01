package cn.ae2bc.client;

import appeng.client.gui.implementations.GuiPatternTerm;
import cn.ae2bc.Ae2bcMod;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

/** Claims the material-output shortcut before inventory sorting mods consume middle-click. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, value = Side.CLIENT)
public final class PatternEncodingTermScreenEvents {
    private PatternEncodingTermScreenEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void mouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiPatternTerm)
                || !Mouse.getEventButtonState() || Mouse.getEventButton() < 0) {
            return;
        }
        GuiPatternTerm screen = (GuiPatternTerm) event.getGui();
        if (PatternEncodingTermScreenSupport.handleMouseClicked(
                screen, screen.getSlotUnderMouse(), Mouse.getEventButton())) {
            event.setCanceled(true);
        }
    }
}
