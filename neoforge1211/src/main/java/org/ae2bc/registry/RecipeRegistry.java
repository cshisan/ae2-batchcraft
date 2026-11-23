package org.ae2bc.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.pattern.Pp2mpPatternConversionRecipe;

public class RecipeRegistry {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(
            Registries.RECIPE_SERIALIZER, Ae2bcMod.MOD_ID
    );

    public static void register(IEventBus bus) {
        Pp2mpPatternConversionRecipe.register();
        RECIPE_SERIALIZERS.register(bus);
    }
}
