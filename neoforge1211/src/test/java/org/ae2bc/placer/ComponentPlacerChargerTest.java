package org.ae2bc.placer;

import appeng.core.definitions.AEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.util.List;

class ComponentPlacerChargerTest {
    @Test
    void frequencyModeDelegatesToTheExtendedChargerDefaultInteraction() {
        Fixture fixture = new Fixture();
        UseItemOnBlockEvent event = fixture.event(InteractionHand.MAIN_HAND, UseItemOnBlockEvent.UsePhase.BLOCK);
        NeoForge.EVENT_BUS.post(event);
        Assertions.assertTrue(event.isCanceled());
        Assertions.assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, event.getCancellationResult());
        Mockito.verify(fixture.player, Mockito.never()).getInventory();
    }

    @Test
    void placementModeSneakingOffhandAndOtherPhasesKeepTheirOwnBehavior() {
        Fixture fixture = new Fixture();
        for (UseItemOnBlockEvent.UsePhase phase : UseItemOnBlockEvent.UsePhase.values()) {
            for (InteractionHand hand : InteractionHand.values()) {
                for (boolean frequency : List.of(false, true)) {
                    for (boolean sneaking : List.of(false, true)) {
                        ComponentPlacerData.frequencyMode(fixture.stack, frequency);
                        Mockito.when(fixture.player.isSecondaryUseActive()).thenReturn(sneaking);
                        UseItemOnBlockEvent event = fixture.event(hand, phase);
                        NeoForge.EVENT_BUS.post(event);
                        Assertions.assertEquals(phase == UseItemOnBlockEvent.UsePhase.BLOCK
                                && hand == InteractionHand.MAIN_HAND && frequency && !sneaking, event.isCanceled());
                    }
                }
            }
        }
    }

    @Test
    void vanillaChargerOtherBlocksAndOtherItemsAreNotIntercepted() {
        Fixture fixture = new Fixture();
        for (BlockState state : List.of(AEBlocks.CHARGER.block().defaultBlockState(), Blocks.CHEST.defaultBlockState())) {
            Mockito.when(fixture.level.getBlockState(BlockPos.ZERO)).thenReturn(state);
            UseItemOnBlockEvent event = fixture.event(InteractionHand.MAIN_HAND, UseItemOnBlockEvent.UsePhase.BLOCK);
            NeoForge.EVENT_BUS.post(event);
            Assertions.assertFalse(event.isCanceled());
        }
        Fixture charger = new Fixture();
        UseOnContext context = new UseOnContext(charger.level, charger.player, InteractionHand.MAIN_HAND,
                new ItemStack(Items.STICK), Fixture.HIT);
        UseItemOnBlockEvent event = new UseItemOnBlockEvent(context, UseItemOnBlockEvent.UsePhase.BLOCK);
        NeoForge.EVENT_BUS.post(event);
        Assertions.assertFalse(event.isCanceled());
    }

    @Test
    void aDeniedInteractionIsNeverOverridden() {
        Fixture fixture = new Fixture();
        UseItemOnBlockEvent event = fixture.event(InteractionHand.MAIN_HAND, UseItemOnBlockEvent.UsePhase.BLOCK);
        event.cancelWithResult(ItemInteractionResult.FAIL);
        NeoForge.EVENT_BUS.post(event);
        Assertions.assertEquals(ItemInteractionResult.FAIL, event.getCancellationResult());
        ComponentPlacerItem.onUseItemOnBlock(event);
        Assertions.assertEquals(ItemInteractionResult.FAIL, event.getCancellationResult());
        Mockito.verify(fixture.level, Mockito.never()).getBlockState(Mockito.any());
    }

    private static final class Fixture {
        private static final BlockHitResult HIT = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);
        private final Player player = Mockito.mock(Player.class);
        private final Level level = Mockito.mock(Level.class);
        private final ItemStack stack = new ItemStack(ItemRegistry.COMPONENT_PLACER.get());

        private Fixture() {
            ComponentPlacerData.frequencyMode(this.stack, true);
            BlockState state = Mockito.mock(BlockState.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
            Mockito.when(state.getBlockHolder()).thenReturn(Holder.Reference.createStandAlone(
                    BuiltInRegistries.BLOCK.asLookup(), ResourceKey.create(
                            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("extendedae", "ex_charger"))));
            Mockito.when(this.level.getBlockState(BlockPos.ZERO)).thenReturn(state);
        }

        private UseItemOnBlockEvent event(InteractionHand hand, UseItemOnBlockEvent.UsePhase phase) {
            return new UseItemOnBlockEvent(new UseOnContext(this.level, this.player, hand, this.stack, Fixture.HIT), phase);
        }
    }
}
