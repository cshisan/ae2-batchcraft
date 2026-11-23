package org.ae2bc.part;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.part.general.Pp2mpAdjacentTarget;
import org.ae2bc.part.general.Pp2mpProviderTarget;
import org.ae2bc.part.general.Pp2mpSendTarget;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class ProviderTargetTest {
    private static final AEKey STONE = AEItemKey.of(Items.STONE);

    @Test
    void actualInsertionRechecksLimitAfterAnotherMachineAddsResources() {
        Fixture fixture = new Fixture();
        fixture.mark(10);
        fixture.stored = 2;
        ResourceTransferPlan plan = fixture.target.plan(ProviderTargetTest.STONE, 100, new WorkBudget(1));
        Assertions.assertEquals(8, plan.amount());
        Assertions.assertEquals(2, fixture.stored);
        fixture.stored += 3;
        Assertions.assertEquals(5, plan.execute(8));
        Assertions.assertEquals(10, fixture.stored);
        Assertions.assertEquals(1, fixture.changed.get());
        Mockito.verify(fixture.storage, Mockito.times(2)).getAvailableStacks(Mockito.any(KeyCounter.class));
    }

    @Test
    void unlimitedAndBlacklistRulesDoNotEnumerateTargetContents() {
        Fixture fixture = new Fixture();
        fixture.mark(0);
        Assertions.assertEquals(64, fixture.target.plan(ProviderTargetTest.STONE, 64,
                new WorkBudget(1)).execute(64));
        Mockito.verify(fixture.storage, Mockito.never()).getAvailableStacks(Mockito.any(KeyCounter.class));
        fixture.mark(1);
        Mockito.when(fixture.upgrades.isInstalled(AEItems.INVERTER_CARD)).thenReturn(true);
        Assertions.assertEquals(0, fixture.target.plan(ProviderTargetTest.STONE, 64, new WorkBudget(1)).amount());
        AEKey dirt = AEItemKey.of(Items.DIRT);
        Assertions.assertEquals(64, fixture.target.plan(dirt, 64, new WorkBudget(1)).execute(64));
        Mockito.verify(fixture.storage, Mockito.never()).getAvailableStacks(Mockito.any(KeyCounter.class));
    }

    @Test
    void disconnectionOrChangedFilterBetweenPlanningAndExecutionRejectsInsertion() {
        Fixture fixture = new Fixture();
        ResourceTransferPlan disconnected = fixture.target.plan(ProviderTargetTest.STONE, 8, new WorkBudget(1));
        fixture.operational = false;
        Assertions.assertEquals(0, disconnected.execute(8));
        fixture.operational = true;
        ResourceTransferPlan filtered = fixture.target.plan(ProviderTargetTest.STONE, 8, new WorkBudget(1));
        fixture.filter.markers().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 0));
        Assertions.assertEquals(0, filtered.execute(8));
        Assertions.assertEquals(0, fixture.stored);
        Assertions.assertEquals(0, fixture.changed.get());
    }

    @Test
    void exhaustedBudgetDoesNotAccessStorageAndRejectionConsumesOneVisit() {
        Fixture fixture = new Fixture();
        WorkBudget budget = new WorkBudget(1);
        fixture.operational = false;
        Assertions.assertEquals(0, fixture.target.plan(ProviderTargetTest.STONE, 8, budget).amount());
        fixture.operational = true;
        Assertions.assertEquals(0, fixture.target.plan(ProviderTargetTest.STONE, 8, budget).amount());
        Assertions.assertEquals(0, budget.remaining());
        Mockito.verifyNoInteractions(fixture.level, fixture.storage);
    }

    private static final class Fixture {
        private final ServerLevel level = Mockito.mock(ServerLevel.class);
        private final Pp2mpSendTarget send = new Pp2mpSendTarget(new Pp2mpAdjacentTarget(
                () -> this.level, () -> BlockPos.ZERO, () -> Direction.NORTH), () -> { });
        private final MEStorage storage = Mockito.mock(MEStorage.class);
        private final IUpgradeInventory upgrades = Mockito.mock(IUpgradeInventory.class);
        private final Pp2mpProviderFilterData filter = new Pp2mpProviderFilterData(
                ConfigInventory.configTypes(18).supportedTypes(Set.of(AEKeyType.items())).build(),
                this.upgrades, () -> { });
        private final AtomicInteger changed = new AtomicInteger();
        private final Pp2mpProviderTarget target = new Pp2mpProviderTarget(
                this.send, () -> this.filter, () -> this.operational, IActionSource.empty(), this.changed::incrementAndGet);
        private boolean operational = true;
        private long stored;

        @SuppressWarnings("UnstableApiUsage")
        private Fixture() {
            Mockito.when(this.level.isLoaded(BlockPos.ZERO)).thenReturn(true);
            Mockito.when(this.level.getCapability(AECapabilities.ME_STORAGE, BlockPos.ZERO, Direction.NORTH))
                    .thenReturn(this.storage);
            Mockito.doAnswer(invocation -> {
                KeyCounter counter = invocation.getArgument(0);
                counter.add(ProviderTargetTest.STONE, this.stored);
                return null;
            }).when(this.storage).getAvailableStacks(Mockito.any(KeyCounter.class));
            Mockito.when(this.storage.insert(Mockito.any(AEKey.class), Mockito.anyLong(),
                    Mockito.any(Actionable.class), Mockito.any(IActionSource.class))).thenAnswer(invocation -> {
                long amount = invocation.getArgument(1);
                if (invocation.getArgument(2) == Actionable.MODULATE) {
                    this.stored += amount;
                }
                return amount;
            });
        }

        private void mark(long limit) {
            this.filter.markers().setStack(0, new GenericStack(ProviderTargetTest.STONE, 0));
            this.filter.setLimit(0, limit);
        }
    }
}
