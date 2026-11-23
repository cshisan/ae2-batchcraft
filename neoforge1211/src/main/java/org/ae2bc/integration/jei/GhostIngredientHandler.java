package org.ae2bc.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMarkerSlot;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMenu;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class GhostIngredientHandler<S extends AEBaseScreen<?>> implements IGhostIngredientHandler<S> {
    private static <I> ItemStack wrapIngredient(ITypedIngredient<I> ingredient) {
        Optional<ItemStack> item = VanillaTypes.ITEM_STACK.castIngredient(ingredient.getIngredient());
        if (item.isPresent()) {
            return item.get().copyWithCount(1);
        }
        Optional<FluidStack> fluid = NeoForgeTypes.FLUID_STACK.castIngredient(ingredient.getIngredient());
        if (fluid.isEmpty() || fluid.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        GenericStack stack = GenericStack.fromFluidStack(fluid.get());
        return stack == null ? ItemStack.EMPTY : GenericStack.wrapInItemStack(stack.what(), 0);
    }

    @Override
    public <I> @NonNull List<Target<I>> getTargetsTyped(@NonNull S screen, @NonNull ITypedIngredient<I> ingredient, boolean doStart) {
        ItemStack wrapped = GhostIngredientHandler.wrapIngredient(ingredient);
        if (wrapped.isEmpty()) {
            return List.of();
        }
        List<Target<I>> targets = new ArrayList<>();
        for (Slot slot : screen.getMenu().getSlots(SlotSemantics.CONFIG)) {
            if (slot.isActive() && slot instanceof FakeSlot fakeSlot && fakeSlot.canSetFilterTo(wrapped)) {
                targets.add(new SlotTarget<>(screen, fakeSlot, ingredient, fakeSlot::setFilterTo));
            }
        }
        this.addConverterTargets(screen, wrapped, ingredient, targets);
        return targets;
    }

    private <I> void addConverterTargets(
            S screen, ItemStack wrapped, ITypedIngredient<I> ingredient, List<Target<I>> targets
    ) {
        for (SlotSemantic semantic : List.of(
                FakeCraftingConverterMenu.INPUT_MARKERS,
                FakeCraftingConverterMenu.OUTPUT_MARKERS
        )) {
            for (Slot slot : screen.getMenu().getSlots(semantic)) {
                if (slot.isActive() && slot instanceof FakeCraftingConverterMarkerSlot markerSlot
                        && markerSlot.canSetFilterTo(wrapped)) {
                    targets.add(new SlotTarget<>(screen, markerSlot, ingredient, markerSlot::setFilterTo));
                }
            }
        }
    }

    @Override
    public void onComplete() {
    }

    private record SlotTarget<I>(
            AEBaseScreen<?> screen, Slot slot, ITypedIngredient<I> ingredient, Consumer<ItemStack> setFilter
    ) implements Target<I> {
        @Override
        public @NonNull Rect2i getArea() {
            return new Rect2i(
                    this.screen.getGuiLeft() + this.slot.x,
                    this.screen.getGuiTop() + this.slot.y,
                    16, 16
            );
        }

        @Override
        public void accept(@NonNull I ignored) {
            ItemStack wrapped = GhostIngredientHandler.wrapIngredient(this.ingredient);
            if (!wrapped.isEmpty()) {
                this.setFilter.accept(wrapped);
            }
        }
    }
}
