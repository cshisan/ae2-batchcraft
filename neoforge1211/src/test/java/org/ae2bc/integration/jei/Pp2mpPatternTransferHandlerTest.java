package org.ae2bc.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Pp2mpPatternTransferHandlerTest {
    private static IRecipeSlotsView slots(List<IRecipeSlotView> inputs) {
        IRecipeSlotsView slots = Mockito.mock(IRecipeSlotsView.class);
        IRecipeSlotView output = Pp2mpPatternTransferHandlerTest.slot(
                new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.GRAVEL, 3)));
        Mockito.when(slots.getSlotViews(RecipeIngredientRole.INPUT)).thenReturn(inputs);
        Mockito.when(slots.getSlotViews(RecipeIngredientRole.OUTPUT)).thenReturn(List.of(output));
        return slots;
    }

    private static IRecipeSlotView slot(ITypedIngredient<?>... ingredients) {
        IRecipeSlotView slot = Mockito.mock(IRecipeSlotView.class);
        List<ITypedIngredient<?>> entries = List.of(ingredients);
        Mockito.when(slot.getAllIngredients()).thenAnswer(invocation -> entries.stream());
        return slot;
    }

    @Test
    void independentMenuCanRegisterWithoutOptionalIntegrationOnClasspath() {
        Pp2mpPatternTransferHandler handler = new Pp2mpPatternTransferHandler(
                Mockito.mock(IRecipeTransferHandlerHelper.class), false);
        Assertions.assertEquals(Pp2mpEncodingTermMenu.class, handler.getContainerClass());
        Assertions.assertSame(Pp2mpEncodingTermMenu.TYPE, handler.getMenuType().orElseThrow());
    }

    @Test
    void mergesIngredientsBeforeCapacityCheckAndPreservesFluidAmount() {
        List<IRecipeSlotView> inputs = new ArrayList<>();
        for (int slot = 0; slot < 12; slot++) {
            inputs.add(Pp2mpPatternTransferHandlerTest.slot(
                    new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE, 2))));
        }
        inputs.add(Pp2mpPatternTransferHandlerTest.slot(
                new Ingredient<>(NeoForgeTypes.FLUID_STACK, new FluidStack(Fluids.WATER, 1000))));
        Pp2mpPatternTransferHandler.ProcessingRecipe plan = this.prepare(inputs, Map.of(), 2, 1);
        Assertions.assertNull(plan.errorKey());
        Assertions.assertEquals(List.of(new GenericStack(AEItemKey.of(Items.STONE), 24),
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)), plan.inputs());
        Assertions.assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GRAVEL), 3)), plan.outputs());
    }

    @Test
    void choosesPreferredInputWithoutCombiningAlternativeIngredients() {
        IRecipeSlotView alternatives = Pp2mpPatternTransferHandlerTest.slot(
                new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE, 2)),
                new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.COBBLESTONE, 2)));
        Pp2mpPatternTransferHandler.ProcessingRecipe plan = this.prepare(
                List.of(alternatives), Map.of(AEItemKey.of(Items.COBBLESTONE), 5), 1, 1);
        Assertions.assertNull(plan.errorKey());
        Assertions.assertEquals(List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 2)), plan.inputs());
    }

    @Test
    void unsupportedResourceRejectsWholeRecipeInsteadOfDroppingOneSlot() {
        Ingredient<String> unsupported = new Ingredient<>(() -> String.class, "unsupported");
        Pp2mpPatternTransferHandler.ProcessingRecipe plan = this.prepare(
                List.of(Pp2mpPatternTransferHandlerTest.slot(
                                new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE))),
                        Pp2mpPatternTransferHandlerTest.slot(unsupported)), Map.of(), 9, 3);
        Assertions.assertEquals("gui.ae2_batchcraft.pattern_transfer.unsupported", plan.errorKey());
        Assertions.assertTrue(plan.inputs().isEmpty());
        Assertions.assertTrue(plan.outputs().isEmpty());
    }

    @Test
    void rejectsOverflowAndExcessSlotsWithoutReturningPartialRecipe() {
        List<IRecipeSlotView> inputs = List.of(Pp2mpPatternTransferHandlerTest.slot(
                        new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE))),
                Pp2mpPatternTransferHandlerTest.slot(
                        new Ingredient<>(VanillaTypes.ITEM_STACK, new ItemStack(Items.COBBLESTONE))));
        Assertions.assertEquals("gui.ae2_batchcraft.pattern_transfer.too_large",
                this.prepare(inputs, Map.of(), 1, 1).errorKey());
        Assertions.assertEquals("gui.ae2_batchcraft.pattern_transfer.too_large",
                this.prepare(inputs, Map.of(), 9, 0).errorKey());
        Pp2mpPatternTransferHandler.ProcessingRecipe overflow = Pp2mpPatternTransferHandler.prepareRecipe(
                Pp2mpPatternTransferHandlerTest.slots(inputs),
                ingredient -> new GenericStack(AEItemKey.of(Items.STONE), Long.MAX_VALUE),
                Map.of(), 9, 3);
        Assertions.assertEquals("gui.ae2_batchcraft.pattern_transfer.amount_overflow", overflow.errorKey());
        Assertions.assertTrue(overflow.inputs().isEmpty());
    }

    @Test
    void inspectionDoesNotConsumeCarriedOrRecipeItems() {
        ItemStack stack = new ItemStack(Items.STONE, 32);
        IRecipeSlotView slot = Pp2mpPatternTransferHandlerTest.slot(new Ingredient<>(VanillaTypes.ITEM_STACK, stack));
        Assertions.assertNull(this.prepare(List.of(slot), Map.of(), 9, 3).errorKey());
        Assertions.assertEquals(32, stack.getCount());
    }

    private Pp2mpPatternTransferHandler.ProcessingRecipe prepare(
            List<IRecipeSlotView> inputs, Map<AEKey, Integer> priorities, int inputSlots, int outputSlots
    ) {
        return Pp2mpPatternTransferHandler.prepareRecipe(Pp2mpPatternTransferHandlerTest.slots(inputs),
                Pp2mpPatternTransferHandler::convertStandardIngredient, priorities, inputSlots, outputSlots);
    }

    private record Ingredient<T>(IIngredientType<T> type, T value) implements ITypedIngredient<T> {
        @Override
        public IIngredientType<T> getType() {
            return this.type;
        }

        @Override
        public T getIngredient() {
            return this.value;
        }
    }
}
