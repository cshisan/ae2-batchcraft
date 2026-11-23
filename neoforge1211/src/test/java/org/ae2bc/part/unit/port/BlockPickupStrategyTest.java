package org.ae2bc.part.unit.port;

import appeng.api.behaviors.PickupSink;
import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.AppEng;
import appeng.util.Platform;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
class BlockPickupStrategyTest {
    private static final Registry<Enchantment> ENCHANTMENTS = BlockPickupStrategyTest.createEnchantments();

    @Test
    void enchantedToolAndNativeEnergyCalculationAreUsedInBothModes() {
        for (boolean collect : List.of(false, true)) {
            try (Fixture fixture = new Fixture()) {
                ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                builder.set(BlockPickupStrategyTest.ENCHANTMENTS.getHolderOrThrow(Enchantments.FORTUNE), 3);
                builder.set(BlockPickupStrategyTest.ENCHANTMENTS.getHolderOrThrow(Enchantments.EFFICIENCY), 2);
                ItemEnchantments enchantments = builder.toImmutable();
                Assertions.assertEquals(PickupStrategy.Result.PICKED_UP,
                        fixture.strategy(enchantments).tryPickup(fixture.energy, fixture.sink(collect)));
                Assertions.assertEquals(enchantments, fixture.harvestTool.get(DataComponents.ENCHANTMENTS));
                Assertions.assertEquals(collect ? 4L : 0L, fixture.returned.getOrDefault(AEItemKey.of(Items.DIAMOND), 0L));
                Assertions.assertEquals(collect ? 0 : 4, fixture.dropped.stream().mapToInt(ItemStack::getCount).sum());
                double expectedPower = (1 + Blocks.STONE.defaultBlockState().getDestroySpeed(fixture.level, BlockPos.ZERO) + 4)
                        * 24 * Math.pow(0.85, 2);
                Assertions.assertEquals(expectedPower, fixture.consumedPower, 0.001);
            }
        }
    }

    @Test
    void silkTouchReachesTheToolAndUnbreakingCanWaivePower() {
        try (Fixture fixture = new Fixture()) {
            ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            builder.set(BlockPickupStrategyTest.ENCHANTMENTS.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
            builder.set(BlockPickupStrategyTest.ENCHANTMENTS.getHolderOrThrow(Enchantments.UNBREAKING), 3);
            RandomSource random = Mockito.mock(RandomSource.class);
            Mockito.when(random.nextInt(4)).thenReturn(1);
            Mockito.when(fixture.level.getRandom()).thenReturn(random);
            ItemEnchantments enchantments = builder.toImmutable();
            Assertions.assertEquals(PickupStrategy.Result.PICKED_UP,
                    fixture.strategy(enchantments).tryPickup(fixture.energy, fixture.sink(true)));
            Assertions.assertEquals(enchantments, fixture.harvestTool.get(DataComponents.ENCHANTMENTS));
            Assertions.assertEquals(0, fixture.consumedPower);
        }
    }

    @Test
    void onlyNewEntitiesFromTheBrokenBlockAreCollected() {
        try (Fixture fixture = new Fixture()) {
            ItemEntity existing = BlockPickupStrategyTest.entity(new ItemStack(Items.DIAMOND, 5));
            ItemEntity created = BlockPickupStrategyTest.entity(new ItemStack(Items.DIAMOND, 3));
            Mockito.when(fixture.level.getEntitiesOfClass(Mockito.eq(ItemEntity.class), Mockito.any(AABB.class)))
                    .thenReturn(List.of(existing)).thenReturn(List.of(existing, created));
            Assertions.assertEquals(PickupStrategy.Result.PICKED_UP,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(true)));
            Assertions.assertEquals(7L, fixture.returned.get(AEItemKey.of(Items.DIAMOND)));
            Mockito.verify(existing, Mockito.never()).discard();
            Mockito.verify(existing, Mockito.never()).getItem();
            Mockito.verify(created).discard();
        }
    }

    @Test
    void disabledPickupLeavesBothOldAndNewEntitiesInTheWorld() {
        try (Fixture fixture = new Fixture()) {
            ItemEntity existing = BlockPickupStrategyTest.entity(new ItemStack(Items.DIAMOND, 5));
            ItemEntity created = BlockPickupStrategyTest.entity(new ItemStack(Items.DIAMOND, 3));
            Mockito.when(fixture.level.getEntitiesOfClass(Mockito.eq(ItemEntity.class), Mockito.any(AABB.class)))
                    .thenReturn(List.of(existing)).thenReturn(List.of(existing, created));
            Assertions.assertEquals(PickupStrategy.Result.PICKED_UP,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(false)));
            Assertions.assertTrue(fixture.returned.isEmpty());
            Assertions.assertEquals(4, fixture.dropped.stream().mapToInt(ItemStack::getCount).sum());
            Assertions.assertEquals(3, created.getItem().getCount());
            Mockito.verify(existing, Mockito.never()).discard();
            Mockito.verify(created, Mockito.never()).discard();
        }
    }

    @Test
    void insertionShortfallDropsOnlyTheUnacceptedRemainder() {
        try (Fixture fixture = new Fixture()) {
            PickupSink sink = (key, amount, mode) -> {
                if (mode == Actionable.SIMULATE) {
                    return amount;
                }
                fixture.returned.merge(key, 2L, Long::sum);
                return 2;
            };
            Assertions.assertEquals(PickupStrategy.Result.PICKED_UP,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, sink));
            Assertions.assertEquals(2L, fixture.returned.get(AEItemKey.of(Items.DIAMOND)));
            Assertions.assertEquals(2, fixture.dropped.stream().mapToInt(ItemStack::getCount).sum());
            Mockito.verify(fixture.level).destroyBlock(BlockPos.ZERO, false);
        }
    }

    @Test
    void refusedBreakDoesNotInsertSpawnOrConsumeEnergy() {
        try (Fixture fixture = new Fixture()) {
            Mockito.when(fixture.level.destroyBlock(BlockPos.ZERO, false)).thenReturn(false);
            Assertions.assertEquals(PickupStrategy.Result.CANT_PICKUP,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(true)));
            Assertions.assertTrue(fixture.returned.isEmpty());
            Assertions.assertTrue(fixture.dropped.isEmpty());
            Assertions.assertEquals(0, fixture.consumedPower);
        }
    }

    @Test
    void blockedDestinationOrMissingPowerNeverBreaksTheBlock() {
        try (Fixture fixture = new Fixture()) {
            Assertions.assertEquals(PickupStrategy.Result.CANT_STORE,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, (key, amount, mode) -> 0));
            Mockito.when(fixture.energy.extractAEPower(Mockito.anyDouble(),
                    Mockito.eq(Actionable.SIMULATE), Mockito.eq(PowerMultiplier.CONFIG))).thenReturn(0.0);
            Assertions.assertEquals(PickupStrategy.Result.CANT_STORE,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(false)));
            Mockito.verify(fixture.level, Mockito.never()).destroyBlock(Mockito.any(), Mockito.anyBoolean());
        }
    }

    @Test
    void airFluidsUnbreakableBlocksAndDeniedAccessAreNotDestroyed() {
        try (Fixture fixture = new Fixture()) {
            for (BlockState state : List.of(Blocks.AIR.defaultBlockState(),
                    Blocks.WATER.defaultBlockState(), Blocks.BEDROCK.defaultBlockState())) {
                Mockito.when(fixture.level.getBlockState(BlockPos.ZERO)).thenReturn(state);
                Assertions.assertEquals(PickupStrategy.Result.CANT_PICKUP,
                        fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(false)));
            }
            Mockito.when(fixture.level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());
            Mockito.when(fixture.level.mayInteract(fixture.player, BlockPos.ZERO)).thenReturn(false);
            Assertions.assertEquals(PickupStrategy.Result.CANT_PICKUP,
                    fixture.strategy(ItemEnchantments.EMPTY).tryPickup(fixture.energy, fixture.sink(false)));
            Mockito.verify(fixture.level, Mockito.never()).destroyBlock(Mockito.any(), Mockito.anyBoolean());
        }
    }

    private static Registry<Enchantment> createEnchantments() {
        MappedRegistry<Enchantment> registry = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        HolderLookup.RegistryLookup<Enchantment> vanilla = VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT);
        for (ResourceKey<Enchantment> key : List.of(
                Enchantments.FORTUNE, Enchantments.SILK_TOUCH, Enchantments.EFFICIENCY, Enchantments.UNBREAKING)) {
            Holder<Enchantment> holder = vanilla.getOrThrow(key);
            registry.register(key, holder.value(), RegistrationInfo.BUILT_IN);
        }
        return registry.freeze();
    }

    private static ItemEntity entity(ItemStack stack) {
        ItemEntity entity = Mockito.mock(ItemEntity.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Mockito.when(entity.isAlive()).thenReturn(true);
        Mockito.when(entity.getItem()).thenReturn(stack);
        return entity;
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final Player player = Mockito.mock(Player.class);
        private final IEnergySource energy = Mockito.mock(IEnergySource.class);
        private final MockedStatic<Platform> platform = Mockito.mockStatic(
                Platform.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        private final MockedStatic<Block> block = Mockito.mockStatic(
                Block.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        private final MockedStatic<AppEng> app = Mockito.mockStatic(
                AppEng.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        private final Map<AEKey, Long> returned = new HashMap<>();
        private final List<ItemStack> dropped = new ArrayList<>();
        private ItemStack harvestTool = ItemStack.EMPTY;
        private double consumedPower;

        private Fixture() {
            Mockito.when(this.level.isLoaded(BlockPos.ZERO)).thenReturn(true);
            Mockito.when(this.level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE.defaultBlockState());
            Mockito.when(this.level.destroyBlock(BlockPos.ZERO, false)).thenReturn(true);
            Mockito.when(this.level.mayInteract(this.player, BlockPos.ZERO)).thenReturn(true);
            Mockito.when(this.level.registryAccess()).thenReturn(
                    new RegistryAccess.ImmutableRegistryAccess(List.of(BlockPickupStrategyTest.ENCHANTMENTS)));
            Mockito.when(this.level.getEntitiesOfClass(Mockito.eq(ItemEntity.class), Mockito.any(AABB.class)))
                    .thenReturn(List.of());
            Mockito.when(this.energy.extractAEPower(Mockito.anyDouble(), Mockito.any(), Mockito.any()))
                    .thenAnswer(invocation -> {
                        double amount = invocation.getArgument(0);
                        if (invocation.getArgument(1) == Actionable.MODULATE) {
                            this.consumedPower += amount;
                        }
                        return amount;
                    });
            this.platform.when(() -> Platform.getFakePlayer(this.level, null)).thenReturn(this.player);
            this.platform.when(() -> Platform.spawnDrops(Mockito.same(this.level), Mockito.eq(BlockPos.ZERO), Mockito.any()))
                    .thenAnswer(invocation -> {
                        List<ItemStack> drops = invocation.getArgument(2);
                        drops.forEach(stack -> this.dropped.add(stack.copy()));
                        return null;
                    });
            this.block.when(() -> Block.getDrops(Mockito.any(BlockState.class), Mockito.same(this.level),
                            Mockito.eq(BlockPos.ZERO), Mockito.isNull(), Mockito.same(this.player), Mockito.any(ItemStack.class)))
                    .thenAnswer(invocation -> {
                        this.harvestTool = invocation.getArgument(5);
                        return List.of(new ItemStack(Items.DIAMOND, 4));
                    });
            this.app.when(AppEng::instance).thenReturn(Mockito.mock(AppEng.class));
        }

        private Pp2mpBlockPickupStrategy strategy(ItemEnchantments enchantments) {
            Pp2mpAdjacentTarget adjacent = new Pp2mpAdjacentTarget(
                    () -> this.level, () -> BlockPos.ZERO, () -> Direction.NORTH);
            return new Pp2mpBlockPickupStrategy(this.level, Objects.requireNonNull(adjacent.resolve()),
                    Mockito.mock(BlockEntity.class), enchantments, null);
        }

        private PickupSink sink(boolean collect) {
            return (key, amount, mode) -> {
                if (mode == Actionable.SIMULATE) {
                    return amount;
                }
                if (!collect) {
                    return 0;
                }
                this.returned.merge(key, amount, Long::sum);
                return amount;
            };
        }

        @Override
        public void close() {
            this.app.close();
            this.block.close();
            this.platform.close();
        }
    }
}
