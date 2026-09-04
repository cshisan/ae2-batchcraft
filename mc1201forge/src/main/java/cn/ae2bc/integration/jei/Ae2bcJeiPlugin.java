package cn.ae2bc.integration.jei;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.client.UnitPortInputConfigScreen;
import cn.ae2bc.client.UnitPortOutputConfigScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

@JeiPlugin
public final class Ae2bcJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation(Ae2bcMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (ModList.get().isLoaded("ae2jeiintegration")) {
            return;
        }

        registration.addGhostIngredientHandler(
                UnitPortInputConfigScreen.class, new UnitPortGhostIngredientHandler<>());
        registration.addGhostIngredientHandler(
                UnitPortOutputConfigScreen.class, new UnitPortGhostIngredientHandler<>());
    }
}
