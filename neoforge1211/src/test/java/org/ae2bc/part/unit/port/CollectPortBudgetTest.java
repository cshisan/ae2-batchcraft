package org.ae2bc.part.unit.port;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerLogic;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerPart;
import org.ae2bc.part.unit.port.part.Pp2mpUnitCollectPortPart;
import org.ae2bc.registry.ItemRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

@SuppressWarnings("UnstableApiUsage")
class CollectPortBudgetTest {
    @Test
    void rejectedEntitiesHaveBoundedVisitsAndDoNotStarveTheLastEntity() {
        Fixture fixture = new Fixture(600);
        ItemEntity last = fixture.entities.getLast();
        Mockito.when(fixture.port.strategy.canPickUpEntity(Mockito.any())).thenAnswer(call -> {
            fixture.attempts.incrementAndGet();
            return call.getArgument(0) == last;
        });
        Assertions.assertFalse(fixture.port.collectProducts(4096));
        Assertions.assertEquals(256, fixture.attempts.get());
        Assertions.assertFalse(fixture.port.collectProducts(4096));
        Assertions.assertEquals(512, fixture.attempts.get());
        Assertions.assertTrue(fixture.port.collectProducts(1));
        Assertions.assertEquals(600, fixture.attempts.get());
        Assertions.assertTrue(last.getItem().isEmpty());
    }

    @Test
    void quantityLimitAndChangedEntityListKeepTheCursorValid() {
        Fixture fixture = new Fixture(4);
        Mockito.when(fixture.port.strategy.canPickUpEntity(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(fixture.port.collectProducts(1));
        Assertions.assertEquals(1, fixture.entities.stream().filter(entity -> entity.getItem().isEmpty()).count());
        fixture.entities.removeFirst();
        Assertions.assertTrue(fixture.port.collectProducts(1));
        fixture.entities.removeIf(entity -> entity.getItem().isEmpty());
        for (int attempt = 0; attempt < 3; attempt++) {
            fixture.port.collectProducts(1);
            fixture.entities.removeIf(entity -> entity.getItem().isEmpty());
        }
        Assertions.assertTrue(fixture.entities.isEmpty());
        Assertions.assertFalse(fixture.port.collectProducts(4096));
    }

    @Test
    void deadEntityDoesNotStopLaterLiveEntities() {
        Fixture fixture = new Fixture(2);
        Mockito.when(fixture.entities.getFirst().isAlive()).thenReturn(false);
        Mockito.when(fixture.port.strategy.canPickUpEntity(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(fixture.port.collectProducts(1));
        Assertions.assertTrue(fixture.entities.getLast().getItem().isEmpty());
        Mockito.verify(fixture.port.strategy, Mockito.times(1)).canPickUpEntity(Mockito.any());
    }

    @Test
    void rejectedEntityDoesNotPreventLaterProductFromBeingCollected() {
        Fixture fixture = new Fixture(2);
        Mockito.when(fixture.entities.getLast().getItem()).thenReturn(new ItemStack(Items.IRON_INGOT));
        fixture.useStatefulPickup(entity -> entity.getItem().is(Items.IRON_INGOT) ? 1 : 0);

        Assertions.assertTrue(fixture.port.collectProducts(1));
        Assertions.assertEquals(2, fixture.attempts.get());
        Assertions.assertEquals(1, fixture.entities.getFirst().getItem().getCount());
        Assertions.assertTrue(fixture.entities.getLast().getItem().isEmpty());
    }

    @Test
    void partiallyAcceptedEntityDoesNotPreventLaterProductFromBeingCollected() {
        Fixture fixture = new Fixture(2);
        Mockito.when(fixture.entities.getFirst().getItem()).thenReturn(new ItemStack(Items.STONE, 2));
        Mockito.when(fixture.entities.getLast().getItem()).thenReturn(new ItemStack(Items.IRON_INGOT));
        fixture.useStatefulPickup(entity -> 1);

        Assertions.assertTrue(fixture.port.collectProducts(2));
        Assertions.assertEquals(2, fixture.attempts.get());
        Assertions.assertEquals(1, fixture.entities.getFirst().getItem().getCount());
        Assertions.assertTrue(fixture.entities.getLast().getItem().isEmpty());
    }

    @Test
    void fullInventoryStillTriesEveryEntityWithinBudgetAndKeepsCursor() {
        Fixture fixture = new Fixture(600);
        AtomicBoolean hasSpace = new AtomicBoolean();
        ItemEntity last = fixture.entities.getLast();
        fixture.useStatefulPickup(entity -> hasSpace.get() && entity == last ? 1 : 0);

        Assertions.assertFalse(fixture.port.collectProducts(4096));
        Assertions.assertEquals(256, fixture.attempts.get());
        Assertions.assertFalse(fixture.port.collectProducts(4096));
        Assertions.assertEquals(512, fixture.attempts.get());
        Assertions.assertTrue(fixture.entities.stream().allMatch(entity -> entity.getItem().getCount() == 1));
        hasSpace.set(true);
        Assertions.assertTrue(fixture.port.collectProducts(1));
        Assertions.assertEquals(600, fixture.attempts.get());
        Assertions.assertTrue(last.getItem().isEmpty());
        Assertions.assertEquals(599, fixture.entities.stream()
                .filter(entity -> entity.getItem().getCount() == 1).count());
    }

    private static final class Fixture {
        private final TestPort port = new TestPort();
        private final List<ItemEntity> entities = new ArrayList<>();
        private final AtomicInteger attempts = new AtomicInteger();

        private Fixture(int count) {
            ServerLevel level = Mockito.mock(ServerLevel.class);
            ChestBlockEntity host = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            host.setLevel(level);
            this.port.setPartHostInfo(Direction.NORTH, Mockito.mock(IPartHost.class), host);
            Mockito.when(level.getBlockState(BlockPos.ZERO.north())).thenReturn(Blocks.AIR.defaultBlockState());
            Mockito.when(level.getEntitiesOfClass(Mockito.eq(ItemEntity.class), Mockito.any(AABB.class)))
                    .thenReturn(this.entities);
            for (int index = 0; index < count; index++) {
                ItemEntity entity = Mockito.mock(ItemEntity.class);
                Mockito.when(entity.isAlive()).thenReturn(true);
                Mockito.when(entity.getItem()).thenReturn(new ItemStack(Items.STONE));
                this.entities.add(entity);
            }
            Mockito.when(this.port.strategy.pickUpEntity(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenAnswer(call -> {
                        ItemEntity entity = call.getArgument(2);
                        entity.getItem().shrink(1);
                        return true;
                    });
        }

        /* 模拟 AE2 策略在拒收或部分接收后锁定，直到 reset 才恢复的行为。 */
        private void useStatefulPickup(ToIntFunction<ItemEntity> acceptedAmount) {
            AtomicBoolean accepting = new AtomicBoolean(true);
            Mockito.doAnswer(call -> {
                accepting.set(true);
                return null;
            }).when(this.port.strategy).reset();
            Mockito.when(this.port.strategy.canPickUpEntity(Mockito.any())).thenReturn(true);
            Mockito.doAnswer(call -> {
                if (!accepting.get()) {
                    return false;
                }
                ItemEntity entity = call.getArgument(2);
                this.attempts.incrementAndGet();
                int inserted = acceptedAmount.applyAsInt(entity);
                accepting.set(inserted >= entity.getItem().getCount());
                entity.getItem().shrink(inserted);
                return true;
            }).when(this.port.strategy).pickUpEntity(Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    private static final class TestPort extends Pp2mpUnitCollectPortPart {
        private final Pp2mpUnitManagerPart manager = Mockito.mock(Pp2mpUnitManagerPart.class);
        private final IGrid testGrid = Mockito.mock(IGrid.class, Mockito.RETURNS_DEEP_STUBS);
        private final PickupStrategy strategy = Mockito.mock(PickupStrategy.class);

        private TestPort() {
            super(ItemRegistry.PP2MP_UNIT_PORT_COLLECT.get());
            Pp2mpUnitManagerLogic logic = Mockito.mock(
                    Pp2mpUnitManagerLogic.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
            Mockito.when(this.manager.getLogic()).thenReturn(logic);
            Mockito.when(logic.isTaskOperational()).thenReturn(true);
        }

        @Override
        public IGrid grid() {
            return this.testGrid;
        }

        @Override
        protected Pp2mpUnitManagerPart findManager() {
            return this.manager;
        }

        @Override
        public boolean isBoundTo(Pp2mpUnitManagerPart candidate) {
            return candidate == this.manager;
        }

        @Override
        protected List<PickupStrategy> getPickupStrategies(Pp2mpAdjacentTarget.Target target) {
            return List.of(this.strategy);
        }
    }
}
