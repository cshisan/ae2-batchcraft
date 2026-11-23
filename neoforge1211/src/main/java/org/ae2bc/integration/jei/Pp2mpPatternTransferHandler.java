package org.ae2bc.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tamaized.ae2jeiintegration.integration.modules.jei.GenericEntryStackHelper;

import java.util.*;
import java.util.function.Function;

final class Pp2mpPatternTransferHandler implements IUniversalRecipeTransferHandler<Pp2mpEncodingTermMenu> {
    private static final Comparator<GridInventoryEntry> INGREDIENT_PRIORITY = Comparator
            .comparing(GridInventoryEntry::isCraftable)
            .thenComparing((GridInventoryEntry entry) ->
                    !(entry.getWhat() instanceof AEItemKey item) || !item.isDamaged())
            .thenComparing(GridInventoryEntry::getStoredAmount);
    private final IRecipeTransferHandlerHelper helper;
    private final Function<ITypedIngredient<?>, @Nullable GenericStack> converter;

    Pp2mpPatternTransferHandler(IRecipeTransferHandlerHelper helper, boolean ae2JeiIntegrationLoaded) {
        this.helper = helper;
        this.converter = ae2JeiIntegrationLoaded
                ? Ae2JeiCompatibility::convert : Pp2mpPatternTransferHandler::convertStandardIngredient;
    }

    /* 按 AE2 的候选优先级选择材料，再合并数量，防止编码器截断超容量配方。 */
    static ProcessingRecipe prepareRecipe(
            IRecipeSlotsView slots, Function<ITypedIngredient<?>, @Nullable GenericStack> converter,
            Map<AEKey, Integer> priorities, int inputSlots, int outputSlots
    ) {
        try {
            List<GenericStack> inputs = Pp2mpPatternTransferHandler.readStacks(
                    slots, RecipeIngredientRole.INPUT, converter, priorities);
            List<GenericStack> outputs = Pp2mpPatternTransferHandler.readStacks(
                    slots, RecipeIngredientRole.OUTPUT, converter, priorities);
            if (inputs == null || outputs == null || inputs.isEmpty() || outputs.isEmpty()) {
                return ProcessingRecipe.invalid("gui.ae2_batchcraft.pattern_transfer.unsupported");
            }
            if (inputs.size() > inputSlots || outputs.size() > outputSlots) {
                return ProcessingRecipe.invalid("gui.ae2_batchcraft.pattern_transfer.too_large");
            }
            return new ProcessingRecipe(inputs, outputs, null);
        } catch (ArithmeticException exception) {
            return ProcessingRecipe.invalid("gui.ae2_batchcraft.pattern_transfer.amount_overflow");
        }
    }

    private static @Nullable List<GenericStack> readStacks(
            IRecipeSlotsView slots, RecipeIngredientRole role,
            Function<ITypedIngredient<?>, @Nullable GenericStack> converter, Map<AEKey, Integer> priorities
    ) {
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        for (IRecipeSlotView slot : slots.getSlotViews(role)) {
            List<ITypedIngredient<?>> candidates = slot.getAllIngredients().toList();
            if (candidates.isEmpty()) {
                continue;
            }
            GenericStack selected = null;
            int selectedPriority = Integer.MIN_VALUE;
            for (ITypedIngredient<?> candidate : candidates) {
                GenericStack stack = converter.apply(candidate);
                if (stack == null || stack.amount() <= 0) {
                    continue;
                }
                int priority = priorities.getOrDefault(stack.what(), Integer.MIN_VALUE);
                if (selected == null || (role == RecipeIngredientRole.INPUT && priority > selectedPriority)) {
                    selected = stack;
                    selectedPriority = priority;
                }
            }
            if (selected == null) {
                return null;
            }
            amounts.merge(selected.what(), selected.amount(), Math::addExact);
        }
        List<GenericStack> result = new ArrayList<>(amounts.size());
        amounts.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return List.copyOf(result);
    }

    /* 没有安装兼容模组时，仍支持标准物品、流体和 AE2 包装的资源。 */
    static @Nullable GenericStack convertStandardIngredient(ITypedIngredient<?> ingredient) {
        return switch (ingredient.getIngredient()) {
            case ItemStack item -> GenericStack.fromItemStack(item);
            case FluidStack fluid -> GenericStack.fromFluidStack(fluid);
            default -> null;
        };
    }

    @Override
    public @NonNull Class<Pp2mpEncodingTermMenu> getContainerClass() {
        return Pp2mpEncodingTermMenu.class;
    }

    @Override
    public @NonNull Optional<MenuType<Pp2mpEncodingTermMenu>> getMenuType() {
        return Optional.of(Pp2mpEncodingTermMenu.TYPE);
    }

    /* 先完整预检，再统一按处理配方写入；仅点击确认时才重置旧配置并发送槽位数据。 */
    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            @NonNull Pp2mpEncodingTermMenu menu, @NonNull Object recipe,
            @NonNull IRecipeSlotsView slots, @NonNull Player player, boolean maxTransfer, boolean doTransfer
    ) {
        Map<AEKey, Integer> priorities = EncodingHelper.getIngredientPriorities(
                menu, Pp2mpPatternTransferHandler.INGREDIENT_PRIORITY);
        ProcessingRecipe plan = Pp2mpPatternTransferHandler.prepareRecipe(slots, this.converter, priorities,
                menu.getProcessingInputSlots().length, menu.getProcessingOutputSlots().length);
        String error = plan.errorKey();
        if (error != null) {
            return this.helper.createUserErrorWithTooltip(Component.translatable(error));
        }
        if (doTransfer) {
            menu.resetRecipeConfiguration();
            EncodingHelper.encodeProcessingRecipe(menu, plan.inputs().stream().map(List::of).toList(), plan.outputs());
        }
        return null;
    }

    record ProcessingRecipe(List<GenericStack> inputs, List<GenericStack> outputs, @Nullable String errorKey) {
        private static ProcessingRecipe invalid(String errorKey) {
            return new ProcessingRecipe(List.of(), List.of(), errorKey);
        }
    }

    /* 第三方类只在兼容模组存在时加载，不将其变成运行时必需依赖。 */
    private static final class Ae2JeiCompatibility {
        private static @Nullable GenericStack convert(ITypedIngredient<?> ingredient) {
            return GenericEntryStackHelper.ingredientToStack(ingredient);
        }
    }
}
