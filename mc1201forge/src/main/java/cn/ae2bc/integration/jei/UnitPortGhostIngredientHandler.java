package cn.ae2bc.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class UnitPortGhostIngredientHandler<S extends AEBaseScreen<?>>
        implements IGhostIngredientHandler<S> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(S screen, ITypedIngredient<I> ingredient,
                                                boolean doStart) {
        ItemStack wrapped = wrapIngredient(ingredient);
        if (wrapped.isEmpty()) {
            return List.of();
        }

        List<Target<I>> targets = new ArrayList<>();
        for (var slot : screen.getMenu().getSlots(SlotSemantics.CONFIG)) {
            if (slot.isActive() && slot instanceof FakeSlot fakeSlot
                    && fakeSlot.canSetFilterTo(wrapped)) {
                targets.add(new SlotTarget<>(screen, fakeSlot, ingredient));
            }
        }
        return targets;
    }

    private static <I> ItemStack wrapIngredient(ITypedIngredient<I> ingredient) {
        var item = VanillaTypes.ITEM_STACK.castIngredient(ingredient.getIngredient());
        if (item.isPresent()) {
            return item.get().copyWithCount(1);
        }

        var fluid = ForgeTypes.FLUID_STACK.castIngredient(ingredient.getIngredient());
        if (fluid.isEmpty() || fluid.get().isEmpty()) {
            return ItemStack.EMPTY;
        }

        GenericStack stack = GenericStack.fromFluidStack(fluid.get());
        return stack == null ? ItemStack.EMPTY
                : GenericStack.wrapInItemStack(stack.what(), 0);
    }

    @Override
    public void onComplete() {
    }

    private static final class SlotTarget<I> implements Target<I> {
        private final AEBaseScreen<?> screen;
        private final FakeSlot slot;
        private final ITypedIngredient<I> ingredient;

        private SlotTarget(AEBaseScreen<?> screen, FakeSlot slot,
                           ITypedIngredient<I> ingredient) {
            this.screen = screen;
            this.slot = slot;
            this.ingredient = ingredient;
        }

        @Override
        public Rect2i getArea() {
            return new Rect2i(screen.getGuiLeft() + slot.x,
                    screen.getGuiTop() + slot.y, 16, 16);
        }

        @Override
        public void accept(I ignored) {
            ItemStack wrapped = wrapIngredient(ingredient);
            if (!wrapped.isEmpty()) {
                slot.setFilterTo(wrapped);
            }
        }
    }
}
