package org.ae2bc.fake_crafting_converter;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.block.AEBaseEntityBlock;
import appeng.items.tools.MemoryCardItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.ae2bc.registry.BlockRegistry;
import org.ae2bc.registry.ItemRegistry;
import org.jspecify.annotations.NonNull;

public final class FakeCraftingConverterBlock extends AEBaseEntityBlock<FakeCraftingConverterBlockEntity> {
    public FakeCraftingConverterBlock() {
        super(Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion());
    }

    @Override
    public FakeCraftingConverterBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get().create(pos, state);
    }

    @Override
    public FakeCraftingConverterBlockEntity getBlockEntity(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FakeCraftingConverterBlockEntity converter ? converter : null;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        FakeCraftingConverterBlockEntity converter = this.getBlockEntity(level, pos);
        if (converter == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            if (converter.hasFrequencyConflict()) {
                player.displayClientMessage(Component.translatable(
                        "message.ae2_batchcraft.fake_crafting_converter.frequency_conflict",
                        Short.toUnsignedInt(converter.getFrequency())), false);
            }
            MenuOpener.open(FakeCraftingConverterMenu.TYPE, player, MenuLocators.forBlockEntity(converter));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected @NonNull ItemInteractionResult useItemOn(
            ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit
    ) {
        if (!(heldItem.getItem() instanceof IMemoryCard memoryCard) || hand == InteractionHand.OFF_HAND) {
            return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
        }
        FakeCraftingConverterBlockEntity converter = this.getBlockEntity(level, pos);
        if (converter == null) {
            return ItemInteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (InteractionUtil.isInAlternateUseMode(player)) {
            if (converter.getFrequency() == 0) {
                memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
                return ItemInteractionResult.SUCCESS;
            }
            MemoryCardItem.clearCard(heldItem);
            heldItem.set(AEComponents.EXPORTED_SETTINGS_SOURCE,
                    ItemRegistry.FAKE_CRAFTING_CONVERTER.get().getDescription());
            heldItem.set(AEComponents.EXPORTED_P2P_TYPE, ItemRegistry.PP2MP_INPUT.get());
            heldItem.set(AEComponents.EXPORTED_P2P_FREQUENCY, converter.getFrequency());
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            return ItemInteractionResult.SUCCESS;
        }
        Item storedType = heldItem.get(AEComponents.EXPORTED_P2P_TYPE);
        Short storedFrequency = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (storedType != ItemRegistry.PP2MP_INPUT.get() || storedFrequency == null || storedFrequency == 0) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return ItemInteractionResult.SUCCESS;
        }
        if (!converter.setFrequency(storedFrequency)) {
            player.displayClientMessage(Component.translatable(
                    "message.ae2_batchcraft.fake_crafting_converter.frequency_used",
                    Short.toUnsignedInt(storedFrequency)), false);
            return ItemInteractionResult.SUCCESS;
        }
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return ItemInteractionResult.SUCCESS;
    }
}
