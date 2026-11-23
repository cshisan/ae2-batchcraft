package org.ae2bc.pattern;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.common.utils.MinecraftHelper;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.pattern.PatternBatchCount;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.ComponentRegistry;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class Pp2mpPatternItem extends EncodedPatternItem<Pp2mpPatternDetails> implements IMenuItem {
    public Pp2mpPatternItem() {
        super(new Item.Properties().stacksTo(64), Pp2mpPatternDetails::new, null);
    }

    public static ItemStack encodePattern(ItemStack vanillaPattern, MaterialOutputAttr config) {
        if (vanillaPattern.isEmpty() || !Pp2mpPatternItem.hasAePatternData(vanillaPattern)) {
            throw new IllegalArgumentException("Expected an encoded AE2 pattern");
        }

        ItemStack pp2mpPattern = new ItemStack(ItemRegistry.PP2MP_PATTERN.get());
        Pp2mpPatternItem.copyAePatternData(vanillaPattern, pp2mpPattern);
        boolean hasData = vanillaPattern.has(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (hasData && config != null && !config.isEmpty()) {
            pp2mpPattern.set(ComponentRegistry.MATERIAL_OUTPUT_CONFIG, config);
        }
        pp2mpPattern.set(DataComponents.MAX_STACK_SIZE, 1);
        return pp2mpPattern;
    }

    public static ItemStack toVanillaPattern(ItemStack pp2mpPattern) {
        if (pp2mpPattern.getItem() != ItemRegistry.PP2MP_PATTERN.get() || !hasAePatternData(pp2mpPattern)) {
            return ItemStack.EMPTY;
        }

        ItemStack vanillaPattern = createVanillaPattern();
        Pp2mpPatternItem.copyAePatternData(pp2mpPattern, vanillaPattern);
        return vanillaPattern;
    }

    private static boolean hasAePatternData(ItemStack stack) {
        return stack.has(AEComponents.ENCODED_PROCESSING_PATTERN);
    }

    public static boolean hasPatternData(ItemStack stack) {
        return hasAePatternData(stack);
    }

    private static ItemStack createVanillaPattern() {
        return AEItems.PROCESSING_PATTERN.stack();
    }

    private static void copyAePatternData(ItemStack source, ItemStack target) {
        if (source.has(AEComponents.ENCODED_PROCESSING_PATTERN)) {
            target.set(AEComponents.ENCODED_PROCESSING_PATTERN, source.get(AEComponents.ENCODED_PROCESSING_PATTERN));
        }
    }

    public static void registerProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ItemRegistry.PP2MP_PATTERN.get(),
                ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "encoded"),
                (stack, level, entity, seed) -> Pp2mpPatternItem.hasPatternData(stack) ? 1.0F : 0.0F
        ));
    }

    /* 添加悬浮提示 */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flags) {
        if (hasPatternData(stack)) {
            super.appendHoverText(stack, context, lines, flags);
        }
        List<Component> pp2mpLines = new ArrayList<>();
        // 添加输出配置
        this.appendMaterialOutputAttr(stack, pp2mpLines);
        // 添加批次数量
        this.appendBatchCount(stack, pp2mpLines);
        GenericStack marker = stack.get(ComponentRegistry.REAL_OUTPUT_MARKER);
        if (marker != null) {
            pp2mpLines.add(Component.translatable("tooltip.ae2_batchcraft.real_output_marker",
                            marker.what().getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!pp2mpLines.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.add(Component.translatable("tooltip.ae2_batchcraft.pp2mp_config")
                    .withStyle(ChatFormatting.AQUA));
            lines.addAll(pp2mpLines);
        }
    }

    /* 添加批次数量提示 */
    private void appendBatchCount(ItemStack stack, List<Component> lines) {
        Long batchCount = stack.get(ComponentRegistry.PATTERN_BATCH_COUNT);
        if (batchCount != null && batchCount > PatternBatchCount.DEFAULT) {
            lines.add(Component.translatable("tooltip.ae2_batchcraft.pattern_batch_count", batchCount)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /* 添加输出配置提示 */
    private void appendMaterialOutputAttr(ItemStack stack, List<Component> lines) {
        AEItemKey definition = AEItemKey.of(stack);
        if (definition == null || definition.get(AEComponents.ENCODED_PROCESSING_PATTERN) == null) {
            return;
        }
        MaterialOutputAttr config = stack.get(ComponentRegistry.MATERIAL_OUTPUT_CONFIG);
        if (config == null || config.isEmpty()) {
            return;
        }
        AEProcessingPattern processingPattern;
        try {
            processingPattern = new AEProcessingPattern(definition);
        } catch (RuntimeException exception) {
            return;
        }
        boolean addedHeader = false;
        List<GenericStack> inputs = processingPattern.getSparseInputs();
        int lastSlot = inputs.size() - 1;
        while (lastSlot >= 0 && inputs.get(lastSlot) == null) {
            lastSlot--;
        }
        for (int slot = 0; slot <= lastSlot; slot++) {
            GenericStack input = inputs.get(slot);
            MaterialOutputMode mode = config.mode(slot);
            if (input == null && mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                mode = MaterialOutputMode.NORMAL;
            }
            Direction direction = config.direction(slot);
            if (mode == MaterialOutputMode.NORMAL && direction == null) {
                continue;
            }
            if (!addedHeader) {
                lines.add(Component.translatable("tooltip.ae2_batchcraft.material_output_config")
                        .withStyle(ChatFormatting.GRAY));
                addedHeader = true;
            }
            Component directionName = Component.translatable(
                    direction == null
                            ? "gui.ae2_batchcraft.direction.auto"
                            : "gui.ae2_batchcraft.direction." + direction.getName()
            );
            Component modeName = Component.translatable(
                    "gui.ae2_batchcraft.pattern.output_mode." + mode.name());
            if (input != null && mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                lines.add(Component.literal("  ").append(
                        Component.translatable("tooltip.ae2_batchcraft.fake_crafting_converter_input.entry",
                                input.what().getDisplayName()).withStyle(ChatFormatting.GRAY)
                ));
            } else {
                Component inputName = input == null
                        ? Component.translatable("gui.ae2_batchcraft.pattern.empty_input_slot", slot + 1)
                        : input.what().getDisplayName();
                lines.add(Component.literal("  ").append(
                        Component.translatable("tooltip.ae2_batchcraft.material_output_config.entry",
                                        inputName, directionName, modeName)
                                .withStyle(ChatFormatting.GRAY)
                ));
            }
        }
    }

    @Override
    public @NonNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        this.clearAttr(stack, player, hand);
        if (!level.isClientSide()) {
            MenuOpener.open(Pp2mpEncodingTermMenu.TYPE, player, MenuLocators.forHand(player, hand));
        }
        InteractionResult result = InteractionResult.sidedSuccess(level.isClientSide());
        return new InteractionResultHolder<>(result, stack);
    }

    @Override
    public @NonNull InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (this.clearAttr(stack, player, context.getHand())) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        IGridNode node = GridHelper.getExposedNode(
                context.getLevel(),
                context.getClickedPos(),
                context.getClickedFace()
        );
        if (node == null || !node.isActive()) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        MenuOpener.open(Pp2mpEncodingTermMenu.TYPE, player, MenuLocators.forItemUseContext(context));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NonNull ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new Pp2mpPatternMenuHost(this, player, locator, hitResult);
    }

    private boolean clearAttr(ItemStack stack, Player player, InteractionHand hand) {
        if (!InteractionUtil.isInAlternateUseMode(player) || !Pp2mpPatternItem.hasAePatternData(stack)) {
            return false;
        }
        if (MinecraftHelper.level(player).get().isClientSide()) {
            return true;
        }

        player.setItemInHand(hand, new ItemStack(ItemRegistry.PP2MP_PATTERN.get()));
        return true;
    }
}
