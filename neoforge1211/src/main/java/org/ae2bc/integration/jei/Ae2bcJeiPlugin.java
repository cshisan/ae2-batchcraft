package org.ae2bc.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModInfoRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.client.screen.Pp2mpProviderFilterScreen;
import org.ae2bc.client.screen.Pp2mpUnitInputTypeScreen;
import org.ae2bc.client.screen.Pp2mpUnitOutputTypeScreen;
import org.jspecify.annotations.NonNull;

@JeiPlugin
public final class Ae2bcJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "jei_plugin");

    @Override
    public @NonNull ResourceLocation getPluginUid() {
        return ID;
    }

    /* 注册模组简称，使 JEI 的模组搜索支持 @ae2bc。 */
    @Override
    public void registerModInfo(@NonNull IModInfoRegistration registration) {
        registration.addModAliases(Ae2bcMod.MOD_ID, "ae2bc", "batchcraft");
    }

    @Override
    public void registerGuiHandlers(@NonNull IGuiHandlerRegistration registration) {
        if (ModList.get().isLoaded("ae2jeiintegration")) {
            return;
        }
        registration.addGhostIngredientHandler(Pp2mpUnitInputTypeScreen.class, new GhostIngredientHandler<>());
        registration.addGhostIngredientHandler(Pp2mpUnitOutputTypeScreen.class, new GhostIngredientHandler<>());
        registration.addGhostIngredientHandler(Pp2mpProviderFilterScreen.class, new GhostIngredientHandler<>());
    }

    /* 为 PP2MP 的独立菜单注册处理样板快捷加载，覆盖联网和精简界面。 */
    @Override
    public void registerRecipeTransferHandlers(@NonNull IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new Pp2mpPatternTransferHandler(
                registration.getTransferHelper(), ModList.get().isLoaded("ae2jeiintegration")
        ));
    }
}
