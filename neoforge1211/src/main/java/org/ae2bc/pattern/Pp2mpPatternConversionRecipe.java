package org.ae2bc.pattern;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedProcessingPattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.ae2bc.registry.RecipeRegistry;
import org.jspecify.annotations.NonNull;

public final class Pp2mpPatternConversionRecipe extends CustomRecipe {
    private static final RecipeSerializer<Pp2mpPatternConversionRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(Pp2mpPatternConversionRecipe::new);

    public Pp2mpPatternConversionRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, @NonNull Level level) {
        if (input.ingredientCount() != 1) {
            return false;
        }

        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                return stack.getItem() instanceof Pp2mpPatternItem;
            }
        }
        return false;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input, HolderLookup.@NonNull Provider registries) {
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack result = AEItems.BLANK_PATTERN.stack();
            EncodedProcessingPattern encodedPattern = stack.get(AEComponents.ENCODED_PROCESSING_PATTERN);
            if (encodedPattern != null) {
                result = AEItems.PROCESSING_PATTERN.stack();
                result.set(AEComponents.ENCODED_PROCESSING_PATTERN, encodedPattern);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width > 0 && height > 0;
    }

    @Override
    public @NonNull RecipeSerializer<?> getSerializer() {
        return Pp2mpPatternConversionRecipe.SERIALIZER;
    }

    public static void register() {
        RecipeRegistry.RECIPE_SERIALIZERS.register(
                "pp2mp_pattern_conversion",
                () -> Pp2mpPatternConversionRecipe.SERIALIZER
        );
    }
}
